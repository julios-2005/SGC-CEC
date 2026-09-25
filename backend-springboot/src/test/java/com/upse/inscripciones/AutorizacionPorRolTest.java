package com.upse.inscripciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica, contra la cadena HTTP real (MockMvc, no mocks), que cada rol
 * puede hacer exactamente lo que AutenticacionInterceptor imponía antes
 * "a mano" y que ahora imponen SecurityConfig#authorizeHttpRequests y los
 * @PreAuthorize de cada controller.
 * <p>
 * Los tests de COBROS existen porque, tras migrar a Spring Security, la lista
 * blanca de ese rol quedó sin migrar y COBROS podía escribir en Descuentos,
 * Especialistas, Planificaciones e Informes Económicos.
 * <p>
 * Nace de un hallazgo de la auditoría: los endpoints de escritura de
 * CoordinadorController (alta/edición/estado/baja) NO estaban en ninguna
 * lista central de rutas-solo-admin; solo un "if" manual dentro de cada
 * método los protegía. Un COORDINADOR con sesión activa podía, antes de
 * este cambio, dar de alta o eliminar coordinadores — lo mismo que un
 * ADMIN_GENERAL. Este test fija ese comportamiento como caso de prueba
 * para que una regresión futura no pase desapercibida otra vez.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:autorizacion-por-rol;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=clave-aislada-de-prueba-nunca-usar-en-produccion-1234567890",
        "app.cors.allowed-origins=http://localhost:4200",
        "spring.mail.host=localhost", "spring.mail.port=1",
        "spring.mail.username=prueba@example.test", "spring.mail.password=solo-prueba"
})
@AutoConfigureMockMvc
@Transactional
class AutorizacionPorRolTest {

    @org.junit.jupiter.api.io.TempDir
    static Path testUploads;

    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository users;
    @Autowired BCryptPasswordEncoder passwords;
    @Autowired RequestMappingHandlerMapping handlerMapping;

    @DynamicPropertySource
    static void isolatedFiles(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> testUploads.toString());
    }

    @Test
    void unCoordinadorYaNoPuedeDarDeAltaNiEliminarCoordinadores() throws Exception {
        String autorizacion = login(Rol.COORDINADOR, "coordinador.autorizacion");

        http.perform(multipart("/api/coordinadores")
                        .param("cedula", "0999999999")
                        .param("nombres", "Intento")
                        .param("apellidos", "No autorizado")
                        .param("telefono", "+593999999999")
                        .param("correo", "")
                        .header("Authorization", autorizacion))
                .andExpect(status().isForbidden());

        http.perform(delete("/api/coordinadores/{id}", 1L).header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
    }

    @Test
    void unCoordinadorNoPuedeListarNiCrearUsuarios() throws Exception {
        String autorizacion = login(Rol.COORDINADOR, "coordinador.usuarios");

        http.perform(get("/api/usuarios").header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
        // Codificar la ruta no debe evadir la restricción (antes lo probaba
        // AutenticacionInterceptorTest; ahora Spring MVC decodifica la ruta
        // antes de resolver el @PreAuthorize del método).
        http.perform(get("/api/%75suarios").header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
        http.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"otro\",\"rol\":\"COORDINADOR\",\"estado\":true}")
                        .header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
    }

    @Test
    void unUsuarioSiPuedeConsultarSuPropiaFichaAunqueNoSeaAdmin() throws Exception {
        Usuario coordinador = users.saveAndFlush(Usuario.builder()
                .nombreUsuario("coordinador.propio")
                .rol(Rol.COORDINADOR)
                .contraseña(passwords.encode("ClaveDePrueba123!"))
                .estado(true).debeCambiarContraseña(false).build());
        String autorizacion = loginComo("coordinador.propio");

        // Antes de este cambio, /api/usuarios/** era admin-only en bloque:
        // ni siquiera consultar la propia ficha era posible sin ser ADMIN_GENERAL.
        http.perform(get("/api/usuarios/{id}", coordinador.getId()).header("Authorization", autorizacion))
                .andExpect(status().isOk());
    }

    @Test
    void elRolCobrosSoloPuedeLeerInscripcionesNuncaExportarNiCambiarEstado() throws Exception {
        String autorizacion = login(Rol.COBROS, "cobros.autorizacion");

        http.perform(get("/api/inscripciones").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        http.perform(get("/api/inscripciones/exportar").header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
        http.perform(get("/api/inscripciones/planificacion/{id}", 1L).header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
        http.perform(patch("/api/inscripciones/{id}/estado", 1L).param("estado", "ACEPTADA")
                        .header("Authorization", autorizacion))
                .andExpect(status().isForbidden());
    }

    /**
     * Antes, AutenticacionInterceptor dejaba a COBROS en una LISTA BLANCA: solo
     * GET de inscripciones (listado, detalle y archivos) y nada más en todo el
     * sistema. Este test lo comprueba contra TODOS los endpoints que Spring MVC
     * tiene registrados (no una lista escrita a mano), de modo que un controller
     * o endpoint nuevo que se olvide bloquear para COBROS hace fallar el test.
     */
    @Test
    void elRolCobrosNoLlegaANingunEndpointFueraDeSuListaBlanca() throws Exception {
        String autorizacion = login(Rol.COBROS, "cobros.todos.los.endpoints");

        List<String> filtrados = new ArrayList<>();
        int verificados = 0;
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) continue;
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            List<RequestMethod> aProbar = metodos.isEmpty()
                    ? List.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                              RequestMethod.PATCH, RequestMethod.DELETE)
                    : List.copyOf(metodos);
            for (String patron : info.getPathPatternsCondition().getPatternValues()) {
                if (!patron.startsWith("/api/")) continue;
                String ruta = patron.replaceAll("\\{[^}/]+}", "1");
                for (RequestMethod metodo : aProbar) {
                    if (cobrosPuedeAcceder(metodo, ruta)) continue;
                    verificados++;
                    int codigo = http.perform(request(HttpMethod.valueOf(metodo.name()), ruta)
                                    .header("Authorization", autorizacion))
                            .andReturn().getResponse().getStatus();
                    if (codigo != 403) filtrados.add(metodo + " " + patron + " -> HTTP " + codigo);
                }
            }
        }

        // Si la enumeración quedara vacía por un cambio de configuración, el test
        // pasaría en verde sin comprobar nada: por eso se exige un mínimo.
        assertTrue(verificados > 50, "Se esperaban muchos endpoints, se verificaron solo " + verificados);
        assertTrue(filtrados.isEmpty(),
                "COBROS no debería poder acceder a estos endpoints:\n" + String.join("\n", filtrados));
    }

    @Test
    void cobrosConservaLoQueSiPuedeHacer() throws Exception {
        String autorizacion = login(Rol.COBROS, "cobros.permitido");

        // Lectura de inscripciones y sesión propia: nunca 401/403.
        http.perform(get("/api/inscripciones").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        http.perform(get("/api/auth/sesion-actual").header("Authorization", autorizacion))
                .andExpect(status().isOk());
        // El detalle y los archivos también pasan la autorización (el recurso puede
        // no existir, pero no debe ser un 401/403 por rol).
        for (String ruta : List.of("/api/inscripciones/999999",
                "/api/inscripciones/archivos/comprobantes-pago/no-existe.pdf")) {
            int codigo = http.perform(get(ruta).header("Authorization", autorizacion))
                    .andReturn().getResponse().getStatus();
            assertTrue(codigo != 401 && codigo != 403, ruta + " devolvió " + codigo);
        }
    }

    @Test
    void endurecerCobrosNoBloqueaAOtrosRoles() throws Exception {
        String coordinador = login(Rol.COORDINADOR, "coordinador.sigue.igual");
        String admin = login(Rol.ADMIN_GENERAL, "admin.sigue.igual");

        for (String autorizacion : List.of(coordinador, admin)) {
            http.perform(get("/api/descuentos").header("Authorization", autorizacion))
                    .andExpect(status().isOk());
            http.perform(get("/api/cursos").header("Authorization", autorizacion))
                    .andExpect(status().isOk());
        }
    }

    /** Lo que COBROS SÍ puede alcanzar: su lista blanca, /api/auth y las rutas públicas. */
    private static boolean cobrosPuedeAcceder(RequestMethod metodo, String ruta) {
        if (ruta.startsWith("/api/auth/")) return true;
        if (metodo == RequestMethod.POST && ruta.equals("/api/inscripciones")) return true; // público
        if (metodo != RequestMethod.GET) return false;
        return ruta.equals("/api/inscripciones")
                || ruta.matches("/api/inscripciones/\\d+")
                || ruta.startsWith("/api/inscripciones/archivos/")
                || ruta.equals("/api/planificaciones/vigentes")   // público
                || ruta.startsWith("/api/cursos/archivos/")        // público
                || ruta.equals("/api/descuentos/activos");         // público
    }

    private String login(Rol rol, String nombreUsuario) throws Exception {
        users.saveAndFlush(Usuario.builder()
                .nombreUsuario(nombreUsuario)
                .rol(rol)
                .contraseña(passwords.encode("ClaveDePrueba123!"))
                .estado(true).debeCambiarContraseña(false).build());
        return loginComo(nombreUsuario);
    }

    private String loginComo(String nombreUsuario) throws Exception {
        var login = http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "usuario", nombreUsuario, "contraseña", "ClaveDePrueba123!"))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + json.readTree(login.getResponse().getContentAsString()).path("token").asText();
    }
}
