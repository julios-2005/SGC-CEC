package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CambioContrasenaRequest;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CambioContrasenaServiceTest {

    private static final String ACTUAL = "Temporal123";

    private UsuarioRepository repo;
    private BCryptPasswordEncoder encoder;
    private CambioContrasenaService servicio;
    private Usuario usuario;

    @BeforeEach
    void setup() {
        repo = mock(UsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        servicio = new CambioContrasenaService(repo, new AuthService(repo, encoder));
        usuario = Usuario.builder().id(5L).nombreUsuario("ana").contraseña(encoder.encode(ACTUAL))
                .rol(Rol.COORDINADOR).debeCambiarContraseña(true).build();
        when(repo.findById(5L)).thenReturn(Optional.of(usuario));
    }

    private static CambioContrasenaRequest peticion(String actual, String nueva, String confirmar) {
        return CambioContrasenaRequest.builder()
                .contraseñaActual(actual).contraseñaNueva(nueva).confirmarContraseña(confirmar).build();
    }

    @Test
    void cambiarGuardaElHashNuevoYLevantaLaObligacionDeCambiarla() {
        servicio.cambiarContraseña(5L, peticion(ACTUAL, "Nueva12345", "Nueva12345"));

        verify(repo).save(usuario);
        assertThat(encoder.matches("Nueva12345", usuario.getContraseña())).isTrue();
        assertThat(encoder.matches(ACTUAL, usuario.getContraseña())).isFalse();
        assertThat(usuario.getDebeCambiarContraseña()).isFalse();
    }

    @Test
    void cambiarObligatoriaTambienGuardaElHashNuevo() {
        servicio.cambiarContraseñaObligatoria(5L, peticion(ACTUAL, "Nueva12345", "Nueva12345"));

        verify(repo).save(usuario);
        assertThat(encoder.matches("Nueva12345", usuario.getContraseña())).isTrue();
        assertThat(usuario.getDebeCambiarContraseña()).isFalse();
    }

    @Test
    void conLaContraseñaActualIncorrectaNoSeCambiaNada() {
        assertThatThrownBy(() -> servicio.cambiarContraseña(5L, peticion("Equivocada1", "Nueva12345", "Nueva12345")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("actual es incorrecta");

        verify(repo, never()).save(any());
        assertThat(encoder.matches(ACTUAL, usuario.getContraseña())).isTrue();
    }

    @Test
    void enElCambioObligatorioLaIncorrectaSeReportaComoTemporalIncorrecta() {
        assertThatThrownBy(() -> servicio.cambiarContraseñaObligatoria(5L, peticion("Equivocada1", "Nueva12345", "Nueva12345")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("temporal es incorrecta");

        verify(repo, never()).save(any());
    }

    @Test
    void laConfirmacionDebeCoincidir() {
        assertThatThrownBy(() -> servicio.cambiarContraseña(5L, peticion(ACTUAL, "Nueva12345", "Otra123456")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("no coinciden");

        verify(repo, never()).save(any());
    }

    @Test
    void laNuevaDebeSerDistintaDeLaActual() {
        assertThatThrownBy(() -> servicio.cambiarContraseña(5L, peticion(ACTUAL, ACTUAL, ACTUAL)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("diferente");
        assertThatThrownBy(() -> servicio.cambiarContraseñaObligatoria(5L, peticion(ACTUAL, ACTUAL, ACTUAL)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("diferente a la temporal");

        verify(repo, never()).save(any());
    }

    @Test
    void laNuevaDebeCumplirElFormato() {
        assertThatThrownBy(() -> servicio.cambiarContraseña(5L, peticion(ACTUAL, "sinmayuscula1", "sinmayuscula1")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("mayúscula");

        verify(repo, never()).save(any());
        assertThat(usuario.getDebeCambiarContraseña()).isTrue();
    }

    @Test
    void unUsuarioInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarContraseña(99L, peticion(ACTUAL, "Nueva12345", "Nueva12345")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
