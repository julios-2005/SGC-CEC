package com.upse.inscripciones.config;

import com.upse.inscripciones.dto.LoginResponse;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.repository.UsuarioRepository;
import com.upse.inscripciones.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AutenticacionInterceptorTest {
    private UsuarioRepository repository;
    private AutenticacionInterceptor interceptor;
    private Usuario user;
    private String token;

    @BeforeEach
    void setup() {
        repository = mock(UsuarioRepository.class);
        JwtService jwt = new JwtService("clave-exclusiva-de-pruebas-unitarias-no-produccion-2026", 60000);
        interceptor = new AutenticacionInterceptor(jwt, repository);
        user = Usuario.builder().id(1L).nombreUsuario("prueba").rol(Rol.COORDINADOR).estado(true).build();
        when(repository.findById(1L)).thenAnswer(call -> Optional.of(user));
        token = jwt.generarToken(LoginResponse.UsuarioLogueadoDTO.builder()
                .id(1L).nombreUsuario("prueba").nombre("Prueba").rol("ADMIN_GENERAL").build());
    }

    @Test
    void catalogoPublicoNoExigeToken() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/planificaciones/vigentes"), new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    void datosPrivadosSinTokenDevuelven401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/cursos"), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void desactivarCuentaInvalidaSuAccesoAunqueElJwtNoHayaVencido() throws Exception {
        user.setEstado(false);
        MockHttpServletResponse response = call("/api/cursos");
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void elRolActualPrevaleceSobreElRolAnteriorDelToken() throws Exception {
        assertThat(call("/api/usuarios").getStatus()).isEqualTo(403);
        user.setRol(Rol.ADMIN_GENERAL);
        assertThat(call("/api/usuarios").getStatus()).isEqualTo(200);
    }

    @Test
    void codificarLaRutaNoEvitaLaRestriccionDeAdministrador() throws Exception {
        assertThat(call("/api/%75suarios").getStatus()).isEqualTo(403);
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
        interceptor.preHandle(request, response, new Object());
        return response;
    }
}
