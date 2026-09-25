package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CursoRequest;
import com.upse.inscripciones.dto.CoordinadorRequest;
import com.upse.inscripciones.dto.EspecialistaRequest;
import com.upse.inscripciones.dto.CambioContrasenaRequest;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.UsuarioRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;

class ValidationRegressionTest {
    @Test
    void nombreLargoExistenteEsCompatibleConLaColumnaDe255Caracteres() {
        CursoRequest curso = new CursoRequest();
        curso.setNombre("Curso ".repeat(35)); curso.setCodigo("CEC-01"); curso.setHoras(20);
        curso.setCosto(new BigDecimal("50.00")); curso.setCuposTotales(30); curso.setModalidad("VIRTUAL");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(curso)).isEmpty();
            curso.setNombre("X".repeat(256));
            assertThat(factory.getValidator().validate(curso)).anyMatch(error -> error.getPropertyPath().toString().equals("nombre"));
        }
    }

    @Test
    void noAceptaCostosNegativosNiHorariosQueExcedanLaColumna() {
        PlanificacionService service = new PlanificacionService(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 1L, 1L,
                LocalDate.of(2026, 9, 1), "18:00", LocalDate.of(2026, 9, 2), Modalidad.VIRTUAL, new BigDecimal("-1")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("costo");
        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 1L, 1L,
                LocalDate.of(2026, 9, 1), "X".repeat(101), LocalDate.of(2026, 9, 2), Modalidad.VIRTUAL, BigDecimal.ZERO))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Horario");
    }

    @Test
    void aceptaTelefonosConPrefijoInternacionalEspaciosYParentesis() {
        CoordinadorRequest coordinador = new CoordinadorRequest();
        coordinador.setCedula("0912345678");
        coordinador.setNombres("Coordinador");
        coordinador.setApellidos("Prueba");
        coordinador.setTelefono("+593 (99) 123-4567");
        coordinador.setCorreo("");

        EspecialistaRequest especialista = new EspecialistaRequest();
        especialista.setCedula("0923456789");
        especialista.setNombres("Especialista");
        especialista.setApellidos("Prueba");
        especialista.setTelefono("00 593 98 123 4567");
        especialista.setCorreo("");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(coordinador)).isEmpty();
            assertThat(factory.getValidator().validate(especialista)).isEmpty();
        }
    }

    @Test
    void cambioObligatorioNoPermiteConservarLaContrasenaTemporal() {
        UsuarioRepository users = mock(UsuarioRepository.class);
        AuthService auth = mock(AuthService.class);
        Usuario usuario = Usuario.builder().id(1L).contraseña("hash-temporal").build();
        when(users.findById(1L)).thenReturn(Optional.of(usuario));
        when(auth.verificarContraseña("Temporal123", "hash-temporal")).thenReturn(true);
        CambioContrasenaService service = new CambioContrasenaService(users, auth);
        CambioContrasenaRequest request = CambioContrasenaRequest.builder()
                .contraseñaActual("Temporal123")
                .contraseñaNueva("Temporal123")
                .confirmarContraseña("Temporal123")
                .build();

        assertThatThrownBy(() -> service.cambiarContraseñaObligatoria(1L, request))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("diferente");
        verify(users, never()).save(any());
    }
}
