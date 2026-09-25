package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.repository.EspecialistaRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El listado de especialistas muestra, por cada uno, o bien "Sin cursos
 * dictados" o una lista con viñetas de sus cursos (vía ListadoPdfHtml.Celda,
 * ya probado aparte); estas pruebas fijan esa decisión y el texto
 * Activo/Disponible según el campo estado. generarListadoPorEspecialista es
 * la variante de un solo especialista, usada en consulta-especialistas.html.
 */
class EspecialistaListadoPdfServiceTest {

    private EspecialistaRepository especialistaRepository;
    private PlanificacionRepository planificacionRepository;
    private PdfRendererService pdfRendererService;
    private EspecialistaListadoPdfService servicio;

    @BeforeEach
    void setup() {
        especialistaRepository = mock(EspecialistaRepository.class);
        planificacionRepository = mock(PlanificacionRepository.class);
        pdfRendererService = mock(PdfRendererService.class);
        servicio = new EspecialistaListadoPdfService(especialistaRepository, planificacionRepository, pdfRendererService);
        when(pdfRendererService.renderizar(any())).thenReturn(new byte[]{1, 2, 3});
    }

    private String render() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        return html.getValue();
    }

    private static Especialista especialista(Long id, boolean estado) {
        return Especialista.builder().id(id).cedula("0912345678").nombres("Carlos").apellidos("Ruiz")
                .especialidad("Finanzas").areaConocimiento("Contabilidad").estado(estado).build();
    }

    // ── generarListado (todos) ──────────────────────────────────────────────

    @Test
    void unEspecialistaSinCursosMuestraElMensajeSinCursosDictados() {
        Especialista d = especialista(1L, false);
        when(especialistaRepository.findAll()).thenReturn(List.of(d));
        when(planificacionRepository.findByEspecialistaId(1L)).thenReturn(List.of());

        servicio.generarListado();

        assertThat(render()).contains("Sin cursos dictados");
    }

    @Test
    void unEspecialistaConCursosMuestraCadaUnoConSuRangoDeFechas() {
        Especialista d = especialista(1L, true);
        Curso curso = Curso.builder().nombre("Excel Avanzado").build();
        Planificacion p = Planificacion.builder().curso(curso)
                .fechaInicio(LocalDate.of(2026, 10, 1)).fechaFin(LocalDate.of(2026, 12, 19)).build();
        when(especialistaRepository.findAll()).thenReturn(List.of(d));
        when(planificacionRepository.findByEspecialistaId(1L)).thenReturn(List.of(p));

        servicio.generarListado();

        assertThat(render()).contains("Excel Avanzado").contains("01/10/2026").contains("19/12/2026");
    }

    @Test
    void elEstadoTrueSeMuestraComoActivoYFalseComoDisponible() {
        Especialista activo = especialista(1L, true);
        Especialista disponible = especialista(2L, false);
        when(especialistaRepository.findAll()).thenReturn(List.of(activo, disponible));
        when(planificacionRepository.findByEspecialistaId(any())).thenReturn(List.of());

        servicio.generarListado();

        String html = render();
        assertThat(html).contains("Activo").contains("Disponible");
    }

    @Test
    void sinEspecialistasRegistradosMuestraElMensajeDeListaVacia() {
        when(especialistaRepository.findAll()).thenReturn(List.of());

        servicio.generarListado();

        assertThat(render()).contains("No hay especialistas registrados.");
    }

    // ── generarListadoPorEspecialista ───────────────────────────────────────

    @Test
    void unEspecialistaInexistenteSeReportaComoNoEncontradoYNoSeRenderizaNada() {
        when(especialistaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generarListadoPorEspecialista(9L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("id 9");

        verify(pdfRendererService, never()).renderizar(any());
    }

    @Test
    void elListadoPorEspecialistaIncluyeElCostoEspecialistaDeCadaCurso() {
        Especialista d = especialista(1L, true);
        Curso curso = Curso.builder().nombre("Excel Avanzado").codigo("009-001").build();
        Planificacion p = Planificacion.builder().curso(curso).modalidad(Modalidad.VIRTUAL)
                .horario("Sábados").fechaInicio(LocalDate.of(2026, 10, 1)).fechaFin(LocalDate.of(2026, 12, 19))
                .costoEspecialista(new BigDecimal("350.00")).build();
        when(especialistaRepository.findById(1L)).thenReturn(Optional.of(d));
        when(planificacionRepository.findByEspecialistaId(1L)).thenReturn(List.of(p));

        servicio.generarListadoPorEspecialista(1L);

        assertThat(render()).contains("Carlos Ruiz").contains("0912345678")
                .contains("Excel Avanzado").contains("350.00");
    }

    @Test
    void unEspecialistaSinCursosDictadosMuestraElMensajeDeListaVaciaEnLaVariantePorEspecialista() {
        Especialista d = especialista(1L, false);
        when(especialistaRepository.findById(1L)).thenReturn(Optional.of(d));
        when(planificacionRepository.findByEspecialistaId(1L)).thenReturn(List.of());

        servicio.generarListadoPorEspecialista(1L);

        assertThat(render()).contains("Este especialista no tiene cursos dictados.");
    }
}
