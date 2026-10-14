package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.EspecialistaResponse;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.EspecialistaRepository;
import com.upse.inscripciones.repository.InformeEconomicoRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de Especialista, análogas a las de Coordinador (cédula/pasaporte,
 * correo, unicidad, bloqueo de borrado), más las propias de este servicio:
 * el código de nacionalidad debe existir en el catálogo, y el estado
 * Activo/Disponible ya no es un interruptor manual sino que se recalcula
 * según si el especialista tiene una planificación vigente hoy.
 */
class EspecialistaServiceTest {

    private EspecialistaRepository repo;
    private InformeEconomicoService informes;
    private InformeEconomicoRepository informesRepository;
    private PlanificacionRepository planificaciones;
    private EspecialistaService servicio;

    @BeforeEach
    void setup() {
        repo = mock(EspecialistaRepository.class);
        informes = mock(InformeEconomicoService.class);
        informesRepository = mock(InformeEconomicoRepository.class);
        planificaciones = mock(PlanificacionRepository.class);
        servicio = new EspecialistaService(repo, informes, informesRepository, planificaciones);
        when(repo.save(any(Especialista.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Especialista existente() {
        return Especialista.builder().id(1L).cedula("0912345678").nombres("Carlos")
                .apellidos("Ruiz").correo("carlos@upse.edu.ec").estado(false).build();
    }

    // ── registrar ────────────────────────────────────────────────────────────

    @Test
    void registrarAceptaCedulaDeDiezDigitosYNormalizaElCorreo() {
        EspecialistaResponse r = servicio.registrar("0912345678", " Carlos ", " Ruiz ", null,
                " CARLOS@UPSE.EDU.EC ", null, null, null);

        assertThat(r.getCedula()).isEqualTo("0912345678");
        assertThat(r.getNombres()).isEqualTo("Carlos");
        assertThat(r.getCorreo()).isEqualTo("carlos@upse.edu.ec");
    }

    @Test
    void registrarAceptaUnPasaporteAlfanumericoDeCincoAVeinteCaracteres() {
        assertThat(servicio.registrar("AB1234", "Carlos", "Ruiz", null, null, null, null, null).getCedula())
                .isEqualTo("AB1234");
    }

    @Test
    void registrarRechazaUnaCedulaInvalidaSinGuardar() {
        assertThatThrownBy(() -> servicio.registrar("1234!", "Carlos", "Ruiz", null, null, null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("cédula de 10 dígitos");

        verify(repo, never()).save(any());
    }

    @Test
    void registrarRechazaNombresOApellidosVacios() {
        assertThatThrownBy(() -> servicio.registrar("0912345678", " ", "Ruiz", null, null, null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("nombres son obligatorios");
        assertThatThrownBy(() -> servicio.registrar("0912345678", "Carlos", null, null, null, null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("apellidos son obligatorios");
    }

    @Test
    void registrarRechazaUnCorreoConFormatoInvalido() {
        assertThatThrownBy(() -> servicio.registrar("0912345678", "Carlos", "Ruiz", null, "no-es-correo", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("formato válido");
    }

    @Test
    void unCorreoVacioOEnBlancoSeGuardaComoNuloSinValidarUnicidad() {
        EspecialistaResponse r = servicio.registrar("0912345678", "Carlos", "Ruiz", null, "   ", null, null, null);

        assertThat(r.getCorreo()).isNull();
        verify(repo, never()).existsByCorreo(anyString());
    }

    @Test
    void registrarRechazaUnaCedulaYaExistente() {
        when(repo.existsByCedula("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("0912345678", "Carlos", "Ruiz", null, null, null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un especialista registrado con la cédula");

        verify(repo, never()).save(any());
    }

    @Test
    void registrarRechazaUnCorreoYaExistente() {
        when(repo.existsByCorreo("carlos@upse.edu.ec")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("0912345678", "Carlos", "Ruiz", null, "carlos@upse.edu.ec", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un especialista registrado con el correo");

        verify(repo, never()).save(any());
    }

    @Test
    void registrarRechazaUnCodigoDeNacionalidadQueNoEstaEnElCatalogo() {
        assertThatThrownBy(() -> servicio.registrar("0912345678", "Carlos", "Ruiz", null, null, null, null, "XX"))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("nacionalidad seleccionada no es válida");
    }

    @Test
    void registrarConUnCodigoDeNacionalidadValidoLoNormalizaAMayusculas() {
        EspecialistaResponse r = servicio.registrar("0912345678", "Carlos", "Ruiz", null, null, null, null, "co");

        assertThat(r.getPaisNacionalidad()).isEqualTo("CO");
    }

    @Test
    void unaNacionalidadEnBlancoSeGuardaComoNulaSinRechazar() {
        EspecialistaResponse r = servicio.registrar("0912345678", "Carlos", "Ruiz", null, null, null, null, "  ");

        assertThat(r.getPaisNacionalidad()).isNull();
    }

    // ── actualizar ───────────────────────────────────────────────────────────

    @Test
    void actualizarConservandoSuPropiaCedulaYCorreoNoLosTomaPorDuplicados() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));

        servicio.actualizar(1L, "0912345678", "Carlos", "Ruiz", null, "carlos@upse.edu.ec", null, null, null);

        verify(repo, never()).existsByCedula(anyString());
        verify(repo, never()).existsByCorreo(anyString());
    }

    @Test
    void actualizarRechazaTomarLaCedulaOElCorreoDeOtroEspecialista() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(repo.existsByCedula("0999999999")).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, "0999999999", "Carlos", "Ruiz", null,
                "carlos@upse.edu.ec", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe otro especialista con la cédula");

        when(repo.existsByCorreo("otro@upse.edu.ec")).thenReturn(true);
        assertThatThrownBy(() -> servicio.actualizar(1L, "0912345678", "Carlos", "Ruiz", null,
                "otro@upse.edu.ec", null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe otro especialista con el correo");
    }

    @Test
    void actualizarUnEspecialistaInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(9L, "0912345678", "Carlos", "Ruiz", null, null, null, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarSinCambiarLaNacionalidadNoDisparaLaValidacionDeDesgloseDeHonorarios() {
        Especialista e = existente();
        e.setPaisNacionalidad("CO");
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(planificaciones.findByEspecialistaId(1L)).thenReturn(java.util.List.of());

        // Misma nacionalidad ("CO" -> "CO"): la validación de desglose de
        // honorarios (que consulta informesRepository) no debe dispararse,
        // aunque sí se recorre la lista de planificaciones al final para
        // recalcular el informe económico.
        servicio.actualizar(1L, "0912345678", "Carlos", "Ruiz", null, "carlos@upse.edu.ec", null, null, "CO");

        verify(informesRepository, never()).findByPlanificacionId(anyLong());
    }

    // ── recalcularEstado ─────────────────────────────────────────────────────

    @Test
    void recalcularEstadoLoMarcaActivoSiTieneUnaPlanificacionVigenteHoy() {
        Especialista e = existente();
        e.setEstado(false);
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(planificaciones.existeActivaParaEspecialista(eq(1L), any(LocalDate.class))).thenReturn(true);

        servicio.recalcularEstado(1L);

        assertThat(e.getEstado()).isTrue();
        verify(repo).save(e);
    }

    @Test
    void recalcularEstadoLoMarcaDisponibleSiNoTieneNingunaPlanificacionVigente() {
        Especialista e = existente();
        e.setEstado(true);
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(planificaciones.existeActivaParaEspecialista(eq(1L), any(LocalDate.class))).thenReturn(false);

        servicio.recalcularEstado(1L);

        assertThat(e.getEstado()).isFalse();
    }

    @Test
    void recalcularEstadoNoGuardaNadaSiElEstadoNoCambio() {
        Especialista e = existente();
        e.setEstado(false);
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(planificaciones.existeActivaParaEspecialista(eq(1L), any(LocalDate.class))).thenReturn(false);

        servicio.recalcularEstado(1L);

        verify(repo, never()).save(any());
    }

    // ── eliminar ─────────────────────────────────────────────────────────────

    @Test
    void noSePuedeEliminarUnEspecialistaComoEspecialistaPrincipalDeUnaPlanificacion() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(planificaciones.existsByEspecialistaId(1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("planificación activa asignada");

        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void noSePuedeEliminarUnEspecialistaQueApareceComoDocenteAdicionalDeUnaPlanificacion() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(planificaciones.existsByEspecialistaId(1L)).thenReturn(false);
        when(planificaciones.existsByDocentesId(1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("planificación activa asignada");

        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void sinPlanificacionesAsociadasSiSePuedeEliminar() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente()));
        when(planificaciones.existsByEspecialistaId(1L)).thenReturn(false);
        when(planificaciones.existsByDocentesId(1L)).thenReturn(false);

        servicio.eliminar(1L);

        verify(repo).deleteById(1L);
    }

    @Test
    void obtenerPorIdInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPorId(9L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
