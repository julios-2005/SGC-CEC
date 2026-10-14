package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cubre las reglas de negocio de PlanificacionService con más impacto: los
 * guardas contra inscripciones ya registradas (que evitan romper datos
 * reales) y que el Informe Económico se recalcule cuando corresponde.
 */
class PlanificacionServiceTest {

    private PlanificacionRepository planificaciones;
    private CoordinadorRepository coordinadores;
    private EspecialistaRepository especialistas;
    private CursoRepository cursos;
    private InscripcionRepository inscripciones;
    private InformeEconomicoService informes;
    private DocentesService docentesService;
    private EspecialistaService especialistaService;
    private PlanificacionService service;

    private void construirService() {
        planificaciones = mock(PlanificacionRepository.class);
        coordinadores = mock(CoordinadorRepository.class);
        especialistas = mock(EspecialistaRepository.class);
        cursos = mock(CursoRepository.class);
        inscripciones = mock(InscripcionRepository.class);
        informes = mock(InformeEconomicoService.class);
        docentesService = mock(DocentesService.class);
        especialistaService = mock(EspecialistaService.class);
        service = new PlanificacionService(planificaciones, coordinadores, especialistas, cursos,
                inscripciones, informes, docentesService, especialistaService);
    }

    private Curso cursoActivo(Long id) {
        return Curso.builder().idCurso(id).nombre("Curso " + id).costo(new BigDecimal("100.00")).build();
    }

    private Coordinador coordinador(Long id, boolean activo) {
        return Coordinador.builder().id(id).nombres("Coord").apellidos(String.valueOf(id)).estado(activo).build();
    }

    private Especialista especialista(Long id) {
        return Especialista.builder().id(id).nombres("Esp").apellidos(String.valueOf(id)).paisNacionalidad("EC").build();
    }

    // ── validarCampos / validarCostoEspecialista (vía registrarPlanificacion) ──

    @Test
    void noPermiteQueLaFechaFinSeaAnteriorALaFechaInicio() {
        construirService();
        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 1L, 1L,
                LocalDate.of(2026, 5, 10), "Lunes 8h-10h", LocalDate.of(2026, 5, 1),
                Modalidad.VIRTUAL, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("Fecha de Fin");
        verifyNoInteractions(cursos, coordinadores, especialistas, informes);
    }

    @Test
    void noPermiteUnCostoDeEspecialistaConMasDeDosDecimales() {
        construirService();
        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 1L, 1L,
                LocalDate.of(2026, 5, 1), "Lunes 8h-10h", LocalDate.of(2026, 5, 10),
                Modalidad.VIRTUAL, new BigDecimal("10.999")))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("costo del especialista");
    }

    @Test
    void noPermiteUnCostoDeEspecialistaNegativoNiPorEncimaDelMaximo() {
        construirService();
        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 1L, 1L,
                LocalDate.of(2026, 5, 1), "Lunes 8h-10h", LocalDate.of(2026, 5, 10),
                Modalidad.VIRTUAL, new BigDecimal("-1.00")))
                .isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 1L, 1L,
                LocalDate.of(2026, 5, 1), "Lunes 8h-10h", LocalDate.of(2026, 5, 10),
                Modalidad.VIRTUAL, new BigDecimal("100000000.00")))
                .isInstanceOf(ValidacionException.class);
    }

    // ── resolverYValidarEntidadesRelacionadas ────────────────────────────

    @Test
    void noPermiteAsignarUnCoordinadorInactivoAUnaPlanificacionNueva() {
        construirService();
        when(cursos.findById(1L)).thenReturn(Optional.of(cursoActivo(1L)));
        when(coordinadores.findById(2L)).thenReturn(Optional.of(coordinador(2L, false)));
        when(especialistas.findById(3L)).thenReturn(Optional.of(especialista(3L)));

        assertThatThrownBy(() -> service.registrarPlanificacion(1L, 2L, 3L,
                LocalDate.of(2026, 5, 1), "Lunes 8h-10h", LocalDate.of(2026, 5, 10),
                Modalidad.VIRTUAL, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("no está activo");
        verifyNoInteractions(informes);
    }

    @Test
    void siPermiteConservarUnCoordinadorQueYaEstabaInactivoAlEditarOtrosCampos() {
        // Si el coordinador YA estaba asignado antes de desactivarse, no debe
        // bloquear la edición de la planificación (solo bloquea asignarlo de
        // nuevo/a otra planificación estando inactivo).
        construirService();
        Curso curso = cursoActivo(1L);
        Coordinador coordinadorInactivo = coordinador(2L, false);
        Planificacion existente = Planificacion.builder().id(5L).curso(curso).coordinador(coordinadorInactivo)
                .especialista(especialista(3L)).fechaInicio(LocalDate.of(2026, 1, 1))
                .fechaFin(LocalDate.of(2026, 1, 10)).build();
        when(planificaciones.findById(5L)).thenReturn(Optional.of(existente));
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));
        when(coordinadores.findById(2L)).thenReturn(Optional.of(coordinadorInactivo));
        when(especialistas.findById(3L)).thenReturn(Optional.of(especialista(3L)));
        when(planificaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizarPlanificacion(5L, 1L, 2L, 3L, LocalDate.of(2026, 5, 1), "Martes 8h-10h",
                LocalDate.of(2026, 5, 10), Modalidad.PRESENCIAL, BigDecimal.ZERO);

        verify(informes).recalcularYGuardar(5L);
    }

    // ── actualizarPlanificacion: no permite cambiar el curso con inscripciones ──

    @Test
    void noPermiteCambiarElCursoDeUnaPlanificacionConInscripciones() {
        construirService();
        Curso cursoOriginal = cursoActivo(1L);
        Planificacion existente = Planificacion.builder().id(5L).curso(cursoOriginal)
                .coordinador(coordinador(2L, true)).especialista(especialista(3L))
                .fechaInicio(LocalDate.of(2026, 1, 1)).fechaFin(LocalDate.of(2026, 1, 10)).build();
        when(planificaciones.findById(5L)).thenReturn(Optional.of(existente));
        when(inscripciones.existsByPlanificacionId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.actualizarPlanificacion(5L, 99L, 2L, 3L,
                LocalDate.of(2026, 5, 1), "Lunes 8h-10h", LocalDate.of(2026, 5, 10),
                Modalidad.VIRTUAL, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("inscripciones registradas");
        verify(planificaciones, never()).save(any());
        verifyNoInteractions(informes);
    }

    @Test
    void siPermiteEditarOtrosCamposDeUnaPlanificacionConInscripcionesMientrasElCursoNoCambie() {
        construirService();
        Curso curso = cursoActivo(1L);
        Coordinador coordinadorActivo = coordinador(2L, true);
        Especialista docente = especialista(3L);
        Planificacion existente = Planificacion.builder().id(5L).curso(curso).coordinador(coordinadorActivo)
                .especialista(docente).fechaInicio(LocalDate.of(2026, 1, 1))
                .fechaFin(LocalDate.of(2026, 1, 10)).build();
        when(planificaciones.findById(5L)).thenReturn(Optional.of(existente));
        when(cursos.findById(1L)).thenReturn(Optional.of(curso));
        when(coordinadores.findById(2L)).thenReturn(Optional.of(coordinadorActivo));
        when(especialistas.findById(3L)).thenReturn(Optional.of(docente));
        when(planificaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizarPlanificacion(5L, 1L, 2L, 3L, LocalDate.of(2026, 6, 1), "Miércoles 8h-10h",
                LocalDate.of(2026, 6, 15), Modalidad.HIBRIDO, new BigDecimal("50.00"));

        // No se consulta si existen inscripciones porque el curso no cambió.
        verifyNoInteractions(inscripciones);
        verify(informes).recalcularYGuardar(5L);
    }

    // ── eliminar ──────────────────────────────────────────────────────────

    @Test
    void noPermiteEliminarUnaPlanificacionConInscripciones() {
        construirService();
        when(planificaciones.existsById(5L)).thenReturn(true);
        when(inscripciones.existsByPlanificacionId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(5L))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("inscripciones registradas");
        verify(planificaciones, never()).deleteById(any());
    }

    @Test
    void eliminarUnaPlanificacionSinInscripcionesRecalculaElEstadoDeSusEspecialistas() {
        construirService();
        Especialista docente = especialista(3L);
        Planificacion existente = Planificacion.builder().id(5L).curso(cursoActivo(1L))
                .coordinador(coordinador(2L, true)).especialista(docente).build();
        when(planificaciones.existsById(5L)).thenReturn(true);
        when(inscripciones.existsByPlanificacionId(5L)).thenReturn(false);
        when(planificaciones.findById(5L)).thenReturn(Optional.of(existente));

        service.eliminar(5L);

        verify(planificaciones).deleteById(5L);
        verify(especialistaService).recalcularEstado(List.of(3L));
    }

    @Test
    void reportaNoEncontradaAlEliminarUnaPlanificacionInexistente() {
        construirService();
        when(planificaciones.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
        verify(planificaciones, never()).deleteById(any());
    }

    // ── actualizarCostoEspecialista ──────────────────────────────────────

    @Test
    void noPermiteEditarElCostoDirectamenteCuandoHayVariosDocentesConHonorariosIndividuales() {
        construirService();
        Especialista docente1 = especialista(3L);
        Especialista docente2 = especialista(4L);
        Planificacion existente = Planificacion.builder().id(5L).curso(cursoActivo(1L))
                .coordinador(coordinador(2L, true)).especialista(docente1)
                .docentes(new java.util.ArrayList<>(List.of(docente1, docente2)))
                .honorarios(new java.util.LinkedHashMap<>(java.util.Map.of(3L, new BigDecimal("50.00"), 4L, new BigDecimal("50.00"))))
                .build();
        when(planificaciones.findById(5L)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.actualizarCostoEspecialista(5L, new BigDecimal("200.00")))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("honorarios individuales");
        verifyNoInteractions(informes);
    }

    @Test
    void editarElCostoConUnSoloDocenteSincronizaElMapaDeHonorarios() {
        construirService();
        Especialista unicoDocente = especialista(3L);
        Planificacion existente = Planificacion.builder().id(5L).curso(cursoActivo(1L))
                .coordinador(coordinador(2L, true)).especialista(unicoDocente).build();
        when(planificaciones.findById(5L)).thenReturn(Optional.of(existente));
        when(planificaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizarCostoEspecialista(5L, new BigDecimal("300.00"));

        org.assertj.core.api.Assertions.assertThat(existente.getHonorarios()).containsEntry(3L, new BigDecimal("300.00"));
        org.assertj.core.api.Assertions.assertThat(existente.getCostoEspecialista()).isEqualByComparingTo("300.00");
        verify(informes).recalcularYGuardar(5L);
    }

    // ── guardarConDocentes ────────────────────────────────────────────────

    @Test
    void guardarConDocentesExigeAlMenosUnDocenteSeleccionado() {
        construirService();
        when(docentesService.resolver(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.guardarConDocentes(null, 1L, 2L, 3L,
                LocalDate.of(2026, 5, 1), "Lunes 8h-10h", LocalDate.of(2026, 5, 10),
                Modalidad.VIRTUAL, BigDecimal.TEN, List.of()))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("al menos un docente");
        verifyNoInteractions(informes);
    }
}
