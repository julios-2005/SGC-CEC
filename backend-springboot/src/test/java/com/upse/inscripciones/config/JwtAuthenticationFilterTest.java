package com.upse.inscripciones.config;

import com.upse.inscripciones.dto.LoginResponse;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.repository.UsuarioRepository;
import com.upse.inscripciones.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Cubre solo AUTENTICACIÓN (¿quién es, sigue activo, debe cambiar su
 * contraseña?). Las pruebas de AUTORIZACIÓN por rol (qué puede hacer cada
 * rol en cada endpoint) que antes vivían aquí junto a las de autenticación
 * se movieron a AutorizacionPorRolTest, como pruebas de integración contra
 * la cadena HTTP real — allí es donde @PreAuthorize realmente se ejecuta.
 */
class JwtAuthenticationFilterTest {
    private UsuarioRepository repository;
    private JwtAuthenticationFilter filter;
    private Usuario user;
    private String token;

    @BeforeEach
    void setup() {
        repository = mock(UsuarioRepository.class);
        JwtService jwt = new JwtService("clave-exclusiva-de-pruebas-unitarias-no-produccion-2026", 60000);
        filter = new JwtAuthenticationFilter(jwt, repository);
        user = Usuario.builder().id(1L).nombreUsuario("prueba").rol(Rol.COORDINADOR).estado(true).build();
        when(repository.findById(1L)).thenAnswer(call -> Optional.of(user));
        token = jwt.generarToken(LoginResponse.UsuarioLogueadoDTO.builder()
                .id(1L).nombreUsuario("prueba").nombre("Prueba").rol("ADMIN_GENERAL").build());
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void catalogoPublicoNoExigeToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/planificaciones/vigentes"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull(); // la cadena continuó
        verifyNoInteractions(repository);
    }

    @Test
    void datosPrivadosSinTokenDevuelven401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/cursos"), response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull(); // la cadena NO continuó
    }

    @Test
    void desactivarCuentaInvalidaSuAccesoAunqueElJwtNoHayaVencido() throws Exception {
        user.setEstado(false);
        assertThat(call("/api/cursos").getStatus()).isEqualTo(401);
    }

    @Test
    void unTokenValidoAutenticaConElRolActualDeLaBaseDeDatos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cursos");
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_COORDINADOR"); // el rol viene de la BD (COORDINADOR), no del claim del token (ADMIN_GENERAL)
    }

    @Test
    void contrasenaTemporalSoloPermiteElFlujoDeCambioYLasRutasPublicas() throws Exception {
        user.setDebeCambiarContraseña(true);
        MockHttpServletResponse response = call("/api/cursos");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CAMBIO_CONTRASENA_REQUERIDO");
        assertThat(call("/api/auth/cambiar-contrase%C3%B1a-obligatoria").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse call(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
