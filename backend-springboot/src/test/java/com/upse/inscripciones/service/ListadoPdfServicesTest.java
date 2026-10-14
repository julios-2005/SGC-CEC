package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.CursoRepository;
import com.upse.inscripciones.repository.EspecialistaRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Los tres servicios de listado PDF deben entregar HTML con todo dato de la
 * base escapado, y no fallar por datos opcionales vacíos (modalidad, fechas).
 */
class ListadoPdfServicesTest {

    private static final String ATAQUE = "<script>alert(1)</script>";

    private PdfRendererService renderer;
    private CoordinadorRepository coordinadores;
    private EspecialistaRepository especialistas;
    private CursoRepository cursos;
    private PlanificacionRepository planificaciones;
    private Planificacion planificacion;

    @BeforeEach
    void setup() {
        renderer = mock(PdfRendererService.class);
        when(renderer.renderizar(anyString())).thenReturn(new byte[] {1});
        coordinadores = mock(CoordinadorRepository.class);
        especialistas = mock(EspecialistaRepository.class);
        cursos = mock(CursoRepository.class);
        planificaciones = mock(PlanificacionRepository.class);

        Especialista docente = Especialista.builder().id(7L).nombres(ATAQUE).apellidos("Docente")
                .cedula("0912345678").especialidad(ATAQUE).areaConocimiento(null).estado(true).build();
        // Sin modalidad ni fechas a propósito: antes esto lanzaba NullPointerException.
        planificacion = Planificacion.builder()
                .curso(Curso.builder().nombre(ATAQUE).codigo(ATAQUE).build())
                .horario(ATAQUE)
                .docentes(List.of(docente))
                .build();
    }

    private String htmlGenerado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(renderer).renderizar(captor.capture());
        return captor.getValue();
    }

    private static void assertEscapado(String html) {
        assertThat(html).doesNotContain(ATAQUE);
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void listadoDeUnCoordinadorEscapaLosDatosYToleraCamposVacios() {
        Coordinador c = Coordinador.builder().id(1L).nombres(ATAQUE).apellidos("Perez").cedula("0912345678").build();
        when(coordinadores.findById(1L)).thenReturn(Optional.of(c));
        when(planificaciones.findByCoordinadorId(1L)).thenReturn(List.of(planificacion));

        byte[] pdf = new CoordinadorListadoPdfService(coordinadores, planificaciones, renderer)
                .generarListadoPorCoordinador(1L);

        assertThat(pdf).isNotEmpty();
        assertEscapado(htmlGenerado());
    }

    @Test
    void listadoDeCursosEscapaLosDatos() {
        when(cursos.findAll()).thenReturn(List.of(
                Curso.builder().nombre(ATAQUE).codigo(ATAQUE).build()));

        new CursoListadoPdfService(cursos, renderer).generarListado();

        assertEscapado(htmlGenerado());
    }

    @Test
    void listadoDeCursosSinCursosMuestraUnMensajeEnVezDeUnaTablaVacia() {
        when(cursos.findAll()).thenReturn(List.of());

        new CursoListadoPdfService(cursos, renderer).generarListado();

        assertThat(htmlGenerado()).contains("No hay cursos registrados.");
    }

    @Test
    void listadoGeneralDeEspecialistasEscapaLosDatosYSusCursos() {
        Especialista e = Especialista.builder().id(7L).nombres(ATAQUE).apellidos(ATAQUE)
                .cedula(ATAQUE).especialidad(ATAQUE).estado(false).build();
        when(especialistas.findAll()).thenReturn(List.of(e));
        planificacion.setFechaInicio(LocalDate.of(2026, 1, 5));
        when(planificaciones.findByEspecialistaId(7L)).thenReturn(List.of(planificacion));

        new EspecialistaListadoPdfService(especialistas, planificaciones, renderer).generarListado();

        String html = htmlGenerado();
        assertEscapado(html);
        assertThat(html).contains("<ul class='lista-cursos'>");
        assertThat(html).contains("05/01/2026 – -");
    }

    @Test
    void listadoDeUnEspecialistaEscapaLosDatos() {
        Especialista e = Especialista.builder().id(7L).nombres(ATAQUE).apellidos("Docente").cedula(ATAQUE).build();
        when(especialistas.findById(7L)).thenReturn(Optional.of(e));
        when(planificaciones.findByEspecialistaId(anyLong())).thenReturn(List.of(planificacion));

        new EspecialistaListadoPdfService(especialistas, planificaciones, renderer).generarListadoPorEspecialista(7L);

        assertEscapado(htmlGenerado());
    }
}
