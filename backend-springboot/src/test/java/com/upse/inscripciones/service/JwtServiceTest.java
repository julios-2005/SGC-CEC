package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.LoginResponse;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    @Test
    void generaYValidaUnTokenConLosDatosDelUsuario() {
        JwtService service = new JwtService(
                "clave-de-prueba-con-mas-de-32-caracteres",
                60_000L);
        LoginResponse.UsuarioLogueadoDTO usuario = LoginResponse.UsuarioLogueadoDTO.builder()
                .id(7L)
                .nombreUsuario("coordinador")
                .nombre("Coordinador CEC")
                .rol("COORDINADOR")
                .build();

        Claims claims = service.validarYLeer(service.generarToken(usuario));

        assertEquals("7", claims.getSubject());
        assertEquals("coordinador", claims.get("usuario", String.class));
        assertEquals("COORDINADOR", claims.get("rol", String.class));
        assertNotNull(claims.getExpiration());
        assertEquals(60, service.getExpiraEnSegundos());
    }
}
