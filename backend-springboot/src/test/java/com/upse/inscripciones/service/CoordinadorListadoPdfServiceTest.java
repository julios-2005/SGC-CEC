package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
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
 * El PDF de "cursos a cargo de un coordinador" delega el escapado a
 * ListadoPdfHtml (ya probado aparte), así que aquí solo se fija que arma las
 * filas correctas a partir de las planificaciones del coordinador, que un
 * coordinador inexistente se reporta como no encontrado, y que la lista
 * vacía usa el mensaje "sin filas".
 */
class CoordinadorListadoPdfServiceTest {

    private CoordinadorRepository coordinadorRepository;
    private PlanificacionRepository planificacionRepository;
    private PdfRendererService pdfRendererService;
    private CoordinadorListadoPdfService servicio;

    @BeforeEach
    void setup() {
        coordinadorRepository = mock(CoordinadorRepository.class);
        planificacionRepository = mock(PlanificacionRepository.class);
        pdfRendererService = mock(PdfRendererService.class);
        servicio = new CoordinadorListadoPdfService(coordinadorRepository, planificacionRepository, pdfRendererService);
        when(pdfRendererService.renderizar(any())).thenReturn(new byte[]{1, 2, 3});
    }

    private static Coordinador coordinador() {
        return Coordinador.builder().id(1L).cedula("0912345678").nombres("Ana").apellidos("Pérez").build();
    }

    @Test
    void unCoordinadorInexistenteSeReportaComoNoEncontradoYNoSeRenderizaNada() {
        when(coordinadorRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generarListadoPorCoordinador(9L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("id 9");

        verify(pdfRendererService, never()).renderizar(any());
    }

    @Test
    void elSubtituloIncluyeElNombreCompletoYLaCedulaDelCoordinador() {
        when(coordinadorRepository.findById(1L)).thenReturn(Optional.of(coordinador()));
        when(planificacionRepository.findByCoordinadorId(1L)).thenReturn(List.of());

        servicio.generarListadoPorCoordinador(1L);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).contains("Ana P\u00e9rez").contains("0912345678");
    }

    @Test
    void sinCursosAsignadosMuestraElMensajeDeListaVacia() {
        when(coordinadorRepository.findById(1L)).thenReturn(Optional.of(coordinador()));
        when(planificacionRepository.findByCoordinadorId(1L)).thenReturn(List.of());

        servicio.generarListadoPorCoordinador(1L);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).contains("Este coordinador no tiene cursos a cargo.");
    }

    @Test
    void cadaPlanificacionDelCoordinadorApareceComoUnaFilaConSusDatos() {
        when(coordinadorRepository.findById(1L)).thenReturn(Optional.of(coordinador()));
        Curso curso = Curso.builder().nombre("Excel Avanzado").codigo("009-001").build();
        Especialista especialista = Especialista.builder().nombres("Carlos").apellidos("Ruiz").build();
        Planificacion p = Planificacion.builder().curso(curso).especialista(especialista)
                .modalidad(Modalidad.VIRTUAL).horario("Sábados 08h00-12h00")
                .fechaInicio(LocalDate.of(2026, 10, 1)).fechaFin(LocalDate.of(2026, 12, 19)).build();
        when(planificacionRepository.findByCoordinadorId(1L)).thenReturn(List.of(p));

        servicio.generarListadoPorCoordinador(1L);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).contains("Excel Avanzado").contains("009-001").contains("VIRTUAL")
                .contains("S\u00e1bados 08h00-12h00").contains("01/10/2026").contains("19/12/2026")
                .contains("Carlos Ruiz");
    }

    @Test
    void unCoordinadorConCaracteresEspecialesQuedaEscapadoEnElHtml() {
        Coordinador c = Coordinador.builder().id(1L).cedula("0912345678")
                .nombres("<b>Ana</b>").apellidos("O'Higgins & Cía").build();
        when(coordinadorRepository.findById(1L)).thenReturn(Optional.of(c));
        when(planificacionRepository.findByCoordinadorId(1L)).thenReturn(List.of());

        servicio.generarListadoPorCoordinador(1L);

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).doesNotContain("<b>Ana</b>").contains("&lt;b&gt;Ana&lt;/b&gt;");
    }

    @Test
    void seRenderizaUsandoElPdfQueDevuelvePdfRendererService() {
        when(coordinadorRepository.findById(1L)).thenReturn(Optional.of(coordinador()));
        when(planificacionRepository.findByCoordinadorId(1L)).thenReturn(List.of());
        byte[] esperado = new byte[]{5, 6, 7};
        when(pdfRendererService.renderizar(any())).thenReturn(esperado);

        assertThat(servicio.generarListadoPorCoordinador(1L)).isSameAs(esperado);
    }
}
