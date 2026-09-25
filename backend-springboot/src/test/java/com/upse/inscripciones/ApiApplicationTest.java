package com.upse.inscripciones;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.repository.UsuarioRepository;
import com.upse.inscripciones.service.ComprobanteMatriculaPdfService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Arranque completo y cadena HTTP real de Spring, sin abrir puertos ni usar servicios externos. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-application;DB_CLOSE_DELAY=-1",
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
class ApiApplicationTest {
    @TempDir static Path testUploads;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository users;
    @Autowired BCryptPasswordEncoder passwords;
    @Autowired ComprobanteMatriculaPdfService receipts;

    @DynamicPropertySource
    static void isolatedFiles(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> testUploads.toString());
    }

    @Test
    void elComprobanteConservaElLogoOriginalAunqueAngularEsteSeparado() throws Exception {
        var logo = new ClassPathResource("reports/icon-512.png");
        assertThat(logo.exists()).isTrue();
        try (var input = logo.getInputStream()) {
            assertThat(ReflectionTestUtils.getField(receipts, "logoBase64"))
                    .isEqualTo(Base64.getEncoder().encodeToString(input.readAllBytes()));
        }
    }

    @Test
    void arrancaConCatalogoPublicoYProtegeLosDatosPrivados() throws Exception {
        http.perform(get("/api/planificaciones/vigentes"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        http.perform(get("/api/descuentos/activos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        var result = http.perform(get("/api/cursos"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void loginEmiteJwtYUnaCuentaDesactivadaNoPuedeReutilizarlo() throws Exception {
        Usuario user = users.saveAndFlush(Usuario.builder().nombreUsuario("prueba.contexto")
                .nombreCompleto("Usuario de prueba").rol(Rol.ADMIN_GENERAL)
                .contraseña(passwords.encode("ClaveDePrueba123!"))
                .estado(true).debeCambiarContraseña(false).build());
        var login = http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"prueba.contexto\",\"contraseña\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exito").value(true)).andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).path("token").asText();
        assertThat(token).isNotBlank();
        http.perform(get("/api/cursos").header("Authorization", "Bearer " + token)
                        .header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        user.setEstado(false);
        users.saveAndFlush(user);
        http.perform(get("/api/cursos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPermiteAngularYRechazaOrigenNoConfigurado() throws Exception {
        http.perform(options("/api/cursos").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        http.perform(options("/api/cursos").header("Origin", "https://no-autorizado.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void flujoPrincipalPermiteCrearCursoPlanificarEInscribirUnEstudiante() throws Exception {
        users.saveAndFlush(Usuario.builder()
                .nombreUsuario("admin.flujo")
                .nombreCompleto("Administrador de prueba")
                .rol(Rol.ADMIN_GENERAL)
                .contraseña(passwords.encode("ClaveDePrueba123!"))
                .estado(true)
                .debeCambiarContraseña(false)
                .build());

        var login = http.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"admin.flujo\",\"contraseña\":\"ClaveDePrueba123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String autorizacion = "Bearer "
                + json.readTree(login.getResponse().getContentAsString()).path("token").asText();

        var cursoCreado = http.perform(multipart("/api/cursos")
                        .param("nombre", "Curso integral de prueba")
                        .param("codigo", "PRUEBA-INT-01")
                        .param("horas", "40")
                        .param("costo", "100.00")
                        .param("cuposTotales", "2")
                        .param("modalidad", "VIRTUAL")
                        .param("estado", "EN_ESPERA")
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Curso integral de prueba"))
                .andExpect(jsonPath("$.cuposRestantes").value(2))
                .andReturn();
        long idCurso = json.readTree(cursoCreado.getResponse().getContentAsString()).path("idCurso").asLong();

        var coordinadorCreado = http.perform(multipart("/api/coordinadores")
                        .param("cedula", "0911111111")
                        .param("nombres", "Coordinador")
                        .param("apellidos", "De Prueba")
                        .param("telefono", "+593991111111")
                        .param("correo", "coordinador@example.test")
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated())
                .andReturn();
        long idCoordinador = json.readTree(coordinadorCreado.getResponse().getContentAsString()).path("id").asLong();

        var especialistaCreado = http.perform(multipart("/api/especialistas")
                        .param("cedula", "0922222222")
                        .param("nombres", "Especialista")
                        .param("apellidos", "De Prueba")
                        .param("telefono", "+593992222222")
                        .param("correo", "especialista@example.test")
                        .param("especialidad", "Capacitación")
                        .param("areaConocimiento", "Educación")
                        .param("paisNacionalidad", "EC")
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated())
                .andReturn();
        long idEspecialista = json.readTree(especialistaCreado.getResponse().getContentAsString()).path("id").asLong();

        LocalDate inicio = LocalDate.now().plusDays(1);
        LocalDate fin = inicio.plusDays(30);
        var planCreado = http.perform(post("/api/planificaciones")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("idCurso", String.valueOf(idCurso))
                        .param("idCoordinador", String.valueOf(idCoordinador))
                        .param("idEspecialista", String.valueOf(idEspecialista))
                        .param("fechaInicio", inicio.toString())
                        .param("fechaFin", fin.toString())
                        .param("horario", "Lunes a viernes 18:00 - 20:00")
                        .param("modalidad", "VIRTUAL")
                        .param("costoEspecialista", "500.00")
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.curso.idCurso").value(idCurso))
                .andReturn();
        long idPlanificacion = json.readTree(planCreado.getResponse().getContentAsString()).path("id").asLong();

        http.perform(get("/api/informes-economicos").header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idPlanificacion == " + idPlanificacion + ")]").exists());
        http.perform(get("/api/informes-economicos/{id}", idPlanificacion).header("Authorization", autorizacion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantes").value(0))
                .andExpect(jsonPath("$.ingresosTotal").value(0)).andExpect(jsonPath("$.egresosTotal").value(500));
        http.perform(post("/api/informes-economicos/{id}/egresos", idPlanificacion)
                        .header("Authorization", autorizacion).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"concepto\":\"Materiales\",\"cantidad\":2,\"valorUnitario\":12.50}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.egresosTotal").value(525))
                .andExpect(jsonPath("$.utilidad").value(-525));

        http.perform(get("/api/planificaciones/vigentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + idPlanificacion + ")]").exists());

        byte[] pdfValido = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);
        var pago = new MockMultipartFile("comprobantePago", "pago.pdf", "application/pdf", pdfValido);
        var cedula = new MockMultipartFile("copiaCedula", "cedula.pdf", "application/pdf", pdfValido);
        var inscripcionCreada = http.perform(multipart("/api/inscripciones")
                        .file(pago)
                        .file(cedula)
                        .param("tipoUsuario", "EXTERNO")
                        .param("nombreCompleto", "Estudiante Integral")
                        .param("idPlanificacion", String.valueOf(idPlanificacion))
                        .param("cedula", "0933333333")
                        .param("telefono", "+593993333333")
                        .param("correoElectronico", "estudiante@example.test")
                        .param("direccion", "Santa Elena")
                        .param("sexo", "FEMENINO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.planificacion.id").value(idPlanificacion))
                .andReturn();
        long idInscripcion = json.readTree(inscripcionCreada.getResponse().getContentAsString()).path("id").asLong();

        http.perform(get("/api/informes-economicos/{id}", idPlanificacion).header("Authorization", autorizacion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantes").value(0));
        for (int intento = 0; intento < 2; intento++) {
            http.perform(patch("/api/inscripciones/{id}/estado", idInscripcion)
                    .param("estado", "ACEPTADA").header("Authorization", autorizacion))
                    .andExpect(status().isOk());
            http.perform(get("/api/informes-economicos/{id}", idPlanificacion).header("Authorization", autorizacion))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.participantes").value(1))
                    .andExpect(jsonPath("$.ingresosTotal").value(100)).andExpect(jsonPath("$.egresosTotal").value(525))
                    .andExpect(jsonPath("$.utilidad").value(-425));
        }

        http.perform(get("/api/cursos/{id}", idCurso).header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuposRestantes").value(1));

        http.perform(get("/api/inscripciones")
                        .param("idPlanificacion", String.valueOf(idPlanificacion))
                        .header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(idInscripcion));

        http.perform(patch("/api/inscripciones/{id}/estado", idInscripcion)
                        .param("estado", "RECHAZADA")
                        .header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        http.perform(get("/api/informes-economicos/{id}", idPlanificacion).header("Authorization", autorizacion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participantes").value(0))
                .andExpect(jsonPath("$.ingresosTotal").value(0)).andExpect(jsonPath("$.egresosTotal").value(525));

        http.perform(get("/api/cursos/{id}", idCurso).header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuposRestantes").value(2));
    }

    @Test
    void guardaVariasPersonasSinCorreoYAceptaTelefonosConFormatoVisual() throws Exception {
        String autorizacion = crearAdminYObtenerAutorizacion("admin.personas");

        for (int i = 1; i <= 2; i++) {
            http.perform(multipart("/api/coordinadores")
                            .param("cedula", "091234567" + i)
                            .param("nombres", "Coordinador " + i)
                            .param("apellidos", "Sin correo")
                            .param("telefono", "+593 (99) 123-456" + i)
                            .param("correo", "")
                            .header("Authorization", autorizacion))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.correo").doesNotExist());

            http.perform(post("/api/especialistas")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("cedula", "092345678" + i)
                            .param("nombres", "Especialista " + i)
                            .param("apellidos", "Sin correo")
                            .param("telefono", "00 593 98 123 456" + i)
                            .param("correo", "")
                            .param("especialidad", "Capacitación")
                            .param("areaConocimiento", "Educación")
                            .param("paisNacionalidad", "EC")
                            .header("Authorization", autorizacion))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.correo").doesNotExist());
        }
    }

    @Test
    void editarUsuarioGuardaElNuevoNombreYEvitaDuplicados() throws Exception {
        String autorizacion = crearAdminYObtenerAutorizacion("admin.usuarios");

        var creado = http.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "nombreUsuario", "  cuenta.inicial  ",
                                "rol", "ADMIN_GENERAL",
                                "estado", true,
                                "nombreCompleto", "Cuenta de prueba")))
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreUsuario").value("cuenta.inicial"))
                .andReturn();
        var creadoJson = json.readTree(creado.getResponse().getContentAsByteArray());
        long id = creadoJson.path("id").asLong();
        String contraseñaTemporal = creadoJson.path("contraseñaTemporal").asText();

        http.perform(put("/api/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"cuenta.renombrada\",\"rol\":\"ADMIN_GENERAL\",\"estado\":true,\"nombreCompleto\":\"Cuenta de prueba\"}")
                        .header("Authorization", autorizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("cuenta.renombrada"));

        assertThat(users.findByNombreUsuario("cuenta.inicial")).isEmpty();
        assertThat(users.findByNombreUsuario("cuenta.renombrada")).isPresent();
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "usuario", " cuenta.renombrada ", "contraseña", contraseñaTemporal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true));

        http.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"otra.cuenta\",\"rol\":\"ADMIN_GENERAL\",\"estado\":true}")
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated());
        http.perform(put("/api/usuarios/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"otra.cuenta\",\"rol\":\"ADMIN_GENERAL\",\"estado\":true}")
                        .header("Authorization", autorizacion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El usuario 'otra.cuenta' ya existe"));
    }

    @Test
    void eliminarCoordinadorConCuentaAsociadaDevuelveUnMensajeClaro() throws Exception {
        String autorizacion = crearAdminYObtenerAutorizacion("admin.relaciones");
        var coordinadorCreado = http.perform(multipart("/api/coordinadores")
                        .param("cedula", "0934567890")
                        .param("nombres", "Coordinador")
                        .param("apellidos", "Con cuenta")
                        .param("telefono", "+593993456789")
                        .param("correo", "")
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated()).andReturn();
        long idCoordinador = json.readTree(coordinadorCreado.getResponse().getContentAsByteArray()).path("id").asLong();

        var usuarioCreado = http.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "nombreUsuario", "coordinador.cuenta",
                                "rol", "COORDINADOR",
                                "estado", true,
                                "idCoordinador", idCoordinador)))
                        .header("Authorization", autorizacion))
                .andExpect(status().isCreated()).andReturn();
        long idUsuario = json.readTree(usuarioCreado.getResponse().getContentAsByteArray()).path("id").asLong();

        http.perform(delete("/api/coordinadores/{id}", idCoordinador)
                        .header("Authorization", autorizacion))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("cuenta de usuario asociada")));

        http.perform(delete("/api/usuarios/{id}/eliminar", idUsuario)
                        .header("Authorization", autorizacion))
                .andExpect(status().isNoContent());
        http.perform(delete("/api/coordinadores/{id}", idCoordinador)
                        .header("Authorization", autorizacion))
                .andExpect(status().isNoContent());
    }

    private String crearAdminYObtenerAutorizacion(String nombreUsuario) throws Exception {
        users.saveAndFlush(Usuario.builder()
                .nombreUsuario(nombreUsuario)
                .nombreCompleto("Administrador de prueba")
                .rol(Rol.ADMIN_GENERAL)
                .contraseña(passwords.encode("ClaveDePrueba123!"))
                .estado(true)
                .debeCambiarContraseña(false)
                .build());
        var login = http.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "usuario", nombreUsuario, "contraseña", "ClaveDePrueba123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andReturn();
        return "Bearer " + json.readTree(login.getResponse().getContentAsString()).path("token").asText();
    }
}
