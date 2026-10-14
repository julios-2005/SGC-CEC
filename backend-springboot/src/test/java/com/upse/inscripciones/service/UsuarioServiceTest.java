package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.RestablecerContrasenaResponse;
import com.upse.inscripciones.dto.UsuarioRequest;
import com.upse.inscripciones.dto.UsuarioResponse;
import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de Usuario: la necesidad de Coordinador según el rol (obligatorio
 * para COORDINADOR, opcional para ADMIN_GENERAL, ninguno para el resto como
 * COBROS), la relación 1:1 usuario-coordinador, y que la contraseña temporal
 * generada cumpla el formato que exige AuthService.
 */
class UsuarioServiceTest {

    private UsuarioRepository usuarios;
    private CoordinadorRepository coordinadores;
    private AuthService auth;
    private UsuarioService servicio;

    @BeforeEach
    void setup() {
        usuarios = mock(UsuarioRepository.class);
        coordinadores = mock(CoordinadorRepository.class);
        auth = new AuthService(usuarios, new BCryptPasswordEncoder(4));
        servicio = new UsuarioService(usuarios, coordinadores, auth);
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) u.setId(1L);
            return u;
        });
    }

    private static UsuarioRequest peticion(String nombreUsuario, String rol, Long idCoordinador, String nombreCompleto) {
        return UsuarioRequest.builder().nombreUsuario(nombreUsuario).rol(rol)
                .idCoordinador(idCoordinador).nombreCompleto(nombreCompleto).estado(true).build();
    }

    private static Coordinador coordinador(long id, boolean activo) {
        return Coordinador.builder().id(id).nombres("Ana").apellidos("Perez").estado(activo).build();
    }

    // ── crearUsuario ─────────────────────────────────────────────────────────

    @Test
    void crearUnCoordinadorExigeUnIdCoordinadorValidoYSinOtraCuenta() {
        assertThatThrownBy(() -> servicio.crearUsuario(peticion("ana", "COORDINADOR", null, null)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Debes seleccionar un coordinador");

        when(coordinadores.findById(5L)).thenReturn(Optional.of(coordinador(5, true)));
        when(usuarios.findByCoordinadorId(5L)).thenReturn(Optional.empty());

        UsuarioResponse r = servicio.crearUsuario(peticion("ana", "COORDINADOR", 5L, null));

        assertThat(r.getCoordinadorId()).isEqualTo(5L);
        assertThat(r.getNombre()).isEqualTo("Ana Perez");
    }

    @Test
    void crearUnCoordinadorRechazaUnCoordinadorInactivoOYaAsociado() {
        when(coordinadores.findById(5L)).thenReturn(Optional.of(coordinador(5, false)));
        assertThatThrownBy(() -> servicio.crearUsuario(peticion("ana", "COORDINADOR", 5L, null)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("no está activo");

        when(coordinadores.findById(6L)).thenReturn(Optional.of(coordinador(6, true)));
        when(usuarios.findByCoordinadorId(6L)).thenReturn(Optional.of(Usuario.builder().id(99L).build()));
        assertThatThrownBy(() -> servicio.crearUsuario(peticion("ana", "COORDINADOR", 6L, null)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("ya tiene un usuario asignado");
    }

    @Test
    void crearUnAdminGeneralPuedeQuedarSinCoordinadorConNombreCompletoPropio() {
        UsuarioResponse r = servicio.crearUsuario(peticion("admin1", "ADMIN_GENERAL", null, " Admin Uno "));

        assertThat(r.getCoordinadorId()).isNull();
        assertThat(r.getNombre()).isEqualTo("Admin Uno");
    }

    @Test
    void crearUnAdminGeneralConCoordinadorSeVinculaIgualQueUnCoordinador() {
        when(coordinadores.findById(5L)).thenReturn(Optional.of(coordinador(5, true)));
        when(usuarios.findByCoordinadorId(5L)).thenReturn(Optional.empty());

        UsuarioResponse r = servicio.crearUsuario(peticion("admin1", "ADMIN_GENERAL", 5L, "Se ignora"));

        assertThat(r.getCoordinadorId()).isEqualTo(5L);
        assertThat(r.getNombre()).isEqualTo("Ana Perez");
    }

    @Test
    void crearUnUsuarioDeOtroRolNuncaLlevaCoordinadorAunqueSeMandeUnId() {
        UsuarioResponse r = servicio.crearUsuario(peticion("cobros1", "COBROS", 5L, "Caja Uno"));

        assertThat(r.getCoordinadorId()).isNull();
        assertThat(r.getNombre()).isEqualTo("Caja Uno");
        verify(coordinadores, never()).findById(any());
    }

    @Test
    void crearRechazaUnNombreDeUsuarioYaExistente() {
        when(usuarios.existsByNombreUsuario("ana")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crearUsuario(peticion("ana", "COBROS", null, null)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("ya existe");

        verify(usuarios, never()).save(any());
    }

    @Test
    void crearRechazaUnRolInvalido() {
        assertThatThrownBy(() -> servicio.crearUsuario(peticion("ana", "SUPERADMIN", null, null)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Rol inválido");
    }

    @Test
    void crearGeneraUnaContraseñaTemporalConElFormatoQueExigeAuthServiceYLaDevuelveEnLaRespuesta() {
        UsuarioResponse r = servicio.crearUsuario(peticion("cobros1", "COBROS", null, "Caja"));

        assertThat(r.getContraseñaTemporal()).startsWith("CEC-").hasSize(12);
        // No lanza: cumple mayúscula, número y largo mínimo.
        auth.validarFormatoContraseña(r.getContraseñaTemporal());
        assertThat(r.getDebeCambiarContraseña()).isNull(); // el flag vive en la entidad, no en este DTO
    }

    @Test
    void unUsuarioNuevoSiempreDebeCambiarLaContraseñaEnElPrimerLogin() {
        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        servicio.crearUsuario(peticion("cobros1", "COBROS", null, "Caja"));

        verify(usuarios).save(captor.capture());
        assertThat(captor.getValue().getDebeCambiarContraseña()).isTrue();
    }

    // ── actualizarUsuario ────────────────────────────────────────────────────

    @Test
    void actualizarUnAdminGeneralQuitandoleElCoordinadorLimpiaElVinculoYPermiteFijarNombre() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("admin1").rol(Rol.ADMIN_GENERAL)
                .coordinador(coordinador(5, true)).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));

        UsuarioResponse r = servicio.actualizarUsuario(1L, peticion("admin1", "ADMIN_GENERAL", null, "Nuevo Nombre"));

        assertThat(r.getCoordinadorId()).isNull();
        assertThat(r.getNombre()).isEqualTo("Nuevo Nombre");
    }

    @Test
    void actualizarUnUsuarioAOtroRolLimpiaCualquierCoordinadorPrevio() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COORDINADOR)
                .coordinador(coordinador(5, true)).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));

        UsuarioResponse r = servicio.actualizarUsuario(1L, peticion("ana", "COBROS", null, "Caja"));

        assertThat(r.getCoordinadorId()).isNull();
        assertThat(u.getCoordinador()).isNull();
    }

    @Test
    void actualizarPermiteConservarElMismoNombreDeUsuarioSinChocarConsigoMismo() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COBROS).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));

        servicio.actualizarUsuario(1L, peticion("ana", "COBROS", null, "Caja"));

        verify(usuarios, never()).existsByNombreUsuario(any());
    }

    @Test
    void actualizarACoordinadorExcluyeAlPropioUsuarioDeLaValidacionDeDuplicado() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COORDINADOR)
                .coordinador(coordinador(5, true)).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));
        when(coordinadores.findById(5L)).thenReturn(Optional.of(coordinador(5, true)));
        when(usuarios.findByCoordinadorId(5L)).thenReturn(Optional.of(u)); // el mismo usuario

        UsuarioResponse r = servicio.actualizarUsuario(1L, peticion("ana", "COORDINADOR", 5L, null));

        assertThat(r.getCoordinadorId()).isEqualTo(5L);
    }

    @Test
    void actualizarRechazaUnNombreDeUsuarioYaTomadoPorOtro() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COBROS).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));
        when(usuarios.existsByNombreUsuario("luis")).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizarUsuario(1L, peticion("luis", "COBROS", null, null)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("ya existe");
    }

    @Test
    void actualizarUnUsuarioInexistenteSeReportaComoNoEncontrado() {
        when(usuarios.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizarUsuario(9L, peticion("ana", "COBROS", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── activar / desactivar / restablecer / eliminar ───────────────────────

    @Test
    void desactivarYActivarCambianElEstado() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COBROS).estado(true).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));

        assertThat(servicio.desactivarUsuario(1L).getEstado()).isFalse();
        assertThat(servicio.activarUsuario(1L).getEstado()).isTrue();
    }

    @Test
    void restablecerContraseñaGeneraUnaNuevaTemporalYObligaACambiarla() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COBROS)
                .contraseña("hash-viejo").debeCambiarContraseña(false).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));

        RestablecerContrasenaResponse r = servicio.restablecerContraseña(1L);

        assertThat(r.getExito()).isTrue();
        assertThat(r.getContraseñaTemporal()).startsWith("CEC-");
        assertThat(u.getContraseña()).isNotEqualTo("hash-viejo");
        assertThat(u.getDebeCambiarContraseña()).isTrue();
    }

    @Test
    void eliminarUsuarioNoEliminaElCoordinadorAsociadoSoloElUsuario() {
        Usuario u = Usuario.builder().id(1L).nombreUsuario("ana").rol(Rol.COORDINADOR)
                .coordinador(coordinador(5, true)).build();
        when(usuarios.findById(1L)).thenReturn(Optional.of(u));

        servicio.eliminarUsuario(1L);

        ((CrudRepository<Usuario, Long>) verify(usuarios)).delete(u);
        ((CrudRepository<Coordinador, Long>) verify(coordinadores, never())).delete(any(Coordinador.class));
        verify(coordinadores, never()).deleteById(any());
    }

    @Test
    void eliminarUnUsuarioInexistenteSeReportaComoNoEncontrado() {
        when(usuarios.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminarUsuario(9L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}