package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CoordinadorResponse;
import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import com.upse.inscripciones.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de Coordinador: cédula o pasaporte válidos, correo con formato
 * válido, unicidad de cédula y correo, y que no se pueda eliminar a un
 * coordinador con una planificación o una cuenta de usuario asociada.
 */
class CoordinadorServiceTest {

    private CoordinadorRepository repo;
    private PlanificacionRepository planificaciones;
    private UsuarioRepository usuarios;
    private FileStorageService archivos;
    private CoordinadorService servicio;

    @BeforeEach
    void setup() {
        repo = mock(CoordinadorRepository.class);
        planificaciones = mock(PlanificacionRepository.class);
        usuarios = mock(UsuarioRepository.class);
        archivos = mock(FileStorageService.class);
        servicio = new CoordinadorService(repo, planificaciones, usuarios, archivos);
        when(repo.save(any(Coordinador.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Coordinador existente() {
        return Coordinador.builder().id(1L).cedula("0912345678").nombres("Ana")
                .apellidos("Perez").correo("ana@upse.edu.ec").estado(true).build();
    }

    // ── registrar ────────────────────────────────────────────────────────────

    @Test
    void registrarAceptaCedulaDeDiezDigitosYNormalizaElCorreo() {
        CoordinadorResponse r = servicio.registrar("0912345678", " Ana ", " Perez ", null,
                " ANA@UPSE.EDU.EC ", null);

        assertThat(r.getCedula()).isEqualTo("0912345678");
        assertThat(r.getNombres()).isEqualTo("Ana");
        assertThat(r.getCorreo()).isEqualTo("ana@upse.edu.ec");
    }

    @Test
    void registrarAceptaUnPasaporteAlfanumericoDeCincoAVeinteCaracteres() {
        assertThat(servicio.registrar("AB1234", "Ana", "Perez", null, null, null).getCedula())
                .isEqualTo("AB1234");
    }

    @Test
    void registrarRechazaUnaCedulaInvalidaSinGuardar() {
        assertThatThrownBy(() -> servicio.registrar("1234!", "Ana", "Perez", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("cédula de 10 dígitos");
        assertThatThrownBy(() -> servicio.registrar(null, "Ana", "Perez", null, null, null))
                .isInstanceOf(ValidacionException.class);

        verify(repo, never()).save(any());
    }

    @Test
    void registrarRechazaNombresOApellidosVacios() {
        assertThatThrownBy(() -> servicio.registrar("0912345678", " ", "Perez", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("nombres son obligatorios");
        assertThatThrownBy(() -> servicio.registrar("0912345678", "Ana", null, null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("apellidos son obligatorios");
    }

    @Test
    void registrarRechazaUnCorreoConFormatoInvalido() {
        assertThatThrownBy(() -> servicio.registrar("0912345678", "Ana", "Perez", null, "no-es-correo", null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("formato válido");
    }

    @Test
    void unCorreoVacioOEnBlancoSeGuardaComoNuloSinValidarUnicidad() {
        CoordinadorResponse r = servicio.registrar("0912345678", "Ana", "Perez", null, "   ", null);

        assertThat(r.getCorreo()).isNull();
        verify(repo, never()).existsByCorreo(anyString());
    }

    @Test
    void registrarRechazaUnaCedulaYaExistente() {
        when(repo.existsByCedula("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("0912345678", "Ana", "Perez", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un coordinador con la cédula");

        verify(repo, never()).save(any());
    }

    @Test
    void registrarRechazaUnCorreoYaExistente() {
        when(repo.existsByCorreo("ana@upse.edu.ec")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("0912345678", "Ana", "Perez", null, "ana@upse.edu.ec", null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un coordinador con el correo");

        verify(repo, never()).save(any());
    }

    @Test
    void sinFotoNoSeLlamaAlAlmacenamientoDeArchivos() {
        servicio.registrar("0912345678", "Ana", "Perez", null, null, null);

        verify(archivos, never()).guardarArchivoOpcional(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void conFotoSeGuardaLaRutaDevueltaPorElAlmacenamiento() {
        MultipartFile foto = mock(MultipartFile.class);
        when(archivos.guardarArchivoOpcional(eq(foto), eq("fotos-coordinadores"), anyString(),
                eq("0912345678"), eq("Ana Perez"))).thenReturn("fotos-coordinadores/x.jpg");

        assertThat(servicio.registrar("0912345678", "Ana", "Perez", null, null, foto).getFoto())
                .isEqualTo("fotos-coordinadores/x.jpg");
    }

    // ── actualizar ───────────────────────────────────────────────────────────

    @Test
    void actualizarConservandoSuPropiaCedulaYCorreoNoLosTomaPorDuplicados() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));

        servicio.actualizar(1L, "0912345678", "Ana", "Perez", null, "ana@upse.edu.ec", null);

        verify(repo, never()).existsByCedula(anyString());
        verify(repo, never()).existsByCorreo(anyString());
    }

    @Test
    void actualizarRechazaTomarLaCedulaOElCorreoDeOtroCoordinador() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(repo.existsByCedula("0999999999")).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, "0999999999", "Ana", "Perez", null, "ana@upse.edu.ec", null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe otro coordinador con la cédula");

        when(repo.existsByCorreo("otro@upse.edu.ec")).thenReturn(true);
        assertThatThrownBy(() -> servicio.actualizar(1L, "0912345678", "Ana", "Perez", null, "otro@upse.edu.ec", null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe otro coordinador con el correo");
    }

    @Test
    void actualizarSinFotoNuevaConservaLaFotoAnterior() {
        Coordinador c = existente();
        c.setFoto("fotos-coordinadores/vieja.jpg");
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        CoordinadorResponse r = servicio.actualizar(1L, "0912345678", "Ana", "Perez", null, "ana@upse.edu.ec", null);

        assertThat(r.getFoto()).isEqualTo("fotos-coordinadores/vieja.jpg");
    }

    @Test
    void actualizarUnCoordinadorInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(9L, "0912345678", "Ana", "Perez", null, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── cambiar estado / eliminar ────────────────────────────────────────────

    @Test
    void cambiarEstadoActualizaElCoordinador() {
        Coordinador c = existente();
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        servicio.cambiarEstado(1L, false);

        assertThat(c.getEstado()).isFalse();
    }

    @Test
    void noSePuedeEliminarUnCoordinadorConUnaPlanificacionAsignada() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(planificaciones.existsByCoordinadorId(1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("planificación activa asignada");

        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void noSePuedeEliminarUnCoordinadorConUnaCuentaDeUsuarioAsociada() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(planificaciones.existsByCoordinadorId(1L)).thenReturn(false);
        when(usuarios.findByCoordinadorId(1L)).thenReturn(Optional.of(
                com.upse.inscripciones.entity.Usuario.builder().id(2L).build()));

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("cuenta de usuario asociada");

        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void sinPlanificacionesNiUsuarioAsociadoSiSePuedeEliminar() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(planificaciones.existsByCoordinadorId(1L)).thenReturn(false);
        when(usuarios.findByCoordinadorId(1L)).thenReturn(Optional.empty());

        servicio.eliminar(1L);

        verify(repo).deleteById(1L);
    }

    @Test
    void obtenerPorIdInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPorId(9L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
