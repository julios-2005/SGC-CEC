package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.LoginRequest;
import com.upse.inscripciones.dto.LoginResponse;
import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String CLAVE = "Segura123";

    private UsuarioRepository repo;
    private BCryptPasswordEncoder encoder;
    private AuthService auth;

    @BeforeEach
    void setup() {
        repo = mock(UsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4); // costo mínimo: es una prueba
        auth = new AuthService(repo, encoder);
    }

    private Usuario usuario(boolean activo) {
        return Usuario.builder().id(9L).nombreUsuario("ana").contraseña(encoder.encode(CLAVE))
                .rol(Rol.COORDINADOR).estado(activo)
                .coordinador(Coordinador.builder().nombres("Ana").apellidos("Perez").build()).build();
    }

    private static LoginRequest peticion(String usuario, String clave) {
        return LoginRequest.builder().usuario(usuario).contraseña(clave).build();
    }

    @Test
    void credencialesCorrectasDevuelvenLosDatosDeLaSesion() {
        when(repo.findByNombreUsuario("ana")).thenReturn(Optional.of(usuario(true)));

        LoginResponse respuesta = auth.validarCredenciales(peticion("ana", CLAVE));

        assertThat(respuesta.getExito()).isTrue();
        assertThat(respuesta.getUsuario().getId()).isEqualTo(9L);
        assertThat(respuesta.getUsuario().getNombreUsuario()).isEqualTo("ana");
        assertThat(respuesta.getUsuario().getNombre()).isEqualTo("Ana Perez");
        assertThat(respuesta.getUsuario().getRol()).isEqualTo("COORDINADOR");
        assertThat(respuesta.getDebeCambiarContraseña()).isFalse();
    }

    @Test
    void elNombreDeUsuarioSeBuscaSinEspaciosAlrededor() {
        when(repo.findByNombreUsuario("ana")).thenReturn(Optional.of(usuario(true)));

        assertThat(auth.validarCredenciales(peticion("  ana  ", CLAVE)).getExito()).isTrue();
    }

    @Test
    void unaContraseñaTemporalIndicaQueDebeCambiarse() {
        Usuario u = usuario(true);
        u.setDebeCambiarContraseña(true);
        when(repo.findByNombreUsuario("ana")).thenReturn(Optional.of(u));

        assertThat(auth.validarCredenciales(peticion("ana", CLAVE)).getDebeCambiarContraseña()).isTrue();
    }

    @Test
    void usuarioInexistenteYContraseñaIncorrectaDanElMismoMensaje() {
        when(repo.findByNombreUsuario("ana")).thenReturn(Optional.of(usuario(true)));

        LoginResponse inexistente = auth.validarCredenciales(peticion("nadie", CLAVE));
        LoginResponse claveMala = auth.validarCredenciales(peticion("ana", "Otra12345"));

        assertThat(inexistente.getExito()).isFalse();
        assertThat(claveMala.getExito()).isFalse();
        // Mismo texto: no debe revelar si el usuario existe.
        assertThat(claveMala.getMensaje()).isEqualTo(inexistente.getMensaje());
        assertThat(claveMala.getUsuario()).isNull();
    }

    @Test
    void unUsuarioDesactivadoNoEntraAunqueLaContraseñaSeaCorrecta() {
        when(repo.findByNombreUsuario("ana")).thenReturn(Optional.of(usuario(false)));

        LoginResponse respuesta = auth.validarCredenciales(peticion("ana", CLAVE));

        assertThat(respuesta.getExito()).isFalse();
        assertThat(respuesta.getMensaje()).contains("desactivado");
        assertThat(respuesta.getUsuario()).isNull();
    }

    @Test
    void unUsuarioNuloSeTrataComoInexistente() {
        assertThat(auth.validarCredenciales(peticion(null, CLAVE)).getExito()).isFalse();
    }

    @Test
    void laContraseñaSeGuardaComoHashBcryptQueLuegoVerifica() {
        String hash = auth.encriptarContraseña(CLAVE);

        assertThat(hash).isNotEqualTo(CLAVE).startsWith("$2");
        assertThat(auth.verificarContraseña(CLAVE, hash)).isTrue();
        assertThat(auth.verificarContraseña("Incorrecta1", hash)).isFalse();
    }

    @Test
    void elFormatoExigeOchoCaracteresUnaMayusculaYUnNumero() {
        assertThatThrownBy(() -> auth.validarFormatoContraseña(null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("8 caracteres");
        assertThatThrownBy(() -> auth.validarFormatoContraseña("Corta1"))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("8 caracteres");
        assertThatThrownBy(() -> auth.validarFormatoContraseña("sinmayuscula1"))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("mayúscula");
        assertThatThrownBy(() -> auth.validarFormatoContraseña("SinNumeroAqui"))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("número");
        auth.validarFormatoContraseña(CLAVE); // válida: no lanza
    }

    @Test
    void noSeEncriptaUnaContraseñaQueNoCumpleElFormato() {
        assertThatThrownBy(() -> auth.encriptarContraseña("debil"))
                .isInstanceOf(ValidacionException.class);
    }
}
