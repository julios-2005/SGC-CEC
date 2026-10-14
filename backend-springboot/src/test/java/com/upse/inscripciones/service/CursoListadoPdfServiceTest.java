package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.repository.CursoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El listado imprimible de cursos delega el escapado a ListadoPdfHtml (ya
 * probado aparte); aquí se fija cómo se arma cada fila a partir del curso —
 * en particular que un costo nulo no se muestra como "$ null" y que los
 * cupos se muestran como "restantes / totales" — y el mensaje cuando no hay
 * cursos registrados.
 */
class CursoListadoPdfServiceTest {

    private CursoRepository cursoRepository;
    private PdfRendererService pdfRendererService;
    private CursoListadoPdfService servicio;

    @BeforeEach
    void setup() {
        cursoRepository = mock(CursoRepository.class);
        pdfRendererService = mock(PdfRendererService.class);
        servicio = new CursoListadoPdfService(cursoRepository, pdfRendererService);
        when(pdfRendererService.renderizar(any())).thenReturn(new byte[]{1, 2, 3});
    }

    private String render() {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        return html.getValue();
    }

    @Test
    void sinCursosRegistradosMuestraElMensajeDeListaVacia() {
        when(cursoRepository.findAll()).thenReturn(List.of());

        servicio.generarListado();

        assertThat(render()).contains("No hay cursos registrados.");
    }

    @Test
    void unCursoConTodosSusDatosMuestraElCostoConSimboloDeDolarYLosCuposComoRestantesSobreTotales() {
        Curso curso = Curso.builder().codigo("009-001").nombre("Excel Avanzado").horas(40)
                .costo(new BigDecimal("120.50")).cuposRestantes(5).cuposTotales(20)
                .modalidad(Modalidad.VIRTUAL).estado(EstadoCurso.EN_ESPERA).build();
        when(cursoRepository.findAll()).thenReturn(List.of(curso));

        servicio.generarListado();

        assertThat(render()).contains("009-001").contains("Excel Avanzado").contains("40")
                .contains("$ 120.50").contains("5 / 20").contains("VIRTUAL").contains("EN_ESPERA");
    }

    @Test
    void unCostoNuloNoSeMuestraComoDolarNull() {
        Curso curso = Curso.builder().codigo("009-002").nombre("Curso Gratuito").horas(10)
                .costo(null).cuposRestantes(1).cuposTotales(1)
                .modalidad(Modalidad.PRESENCIAL).estado(EstadoCurso.EN_ESPERA).build();
        when(cursoRepository.findAll()).thenReturn(List.of(curso));

        servicio.generarListado();

        assertThat(render()).doesNotContain("$ null").contains("-");
    }

    @Test
    void cuposNulosSeMuestranComoGuionEnLugarDeLaPalabraNull() {
        Curso curso = Curso.builder().codigo("009-003").nombre("Curso Sin Cupos Definidos").horas(10)
                .costo(new BigDecimal("10")).cuposRestantes(null).cuposTotales(null)
                .modalidad(Modalidad.PRESENCIAL).estado(EstadoCurso.EN_ESPERA).build();
        when(cursoRepository.findAll()).thenReturn(List.of(curso));

        servicio.generarListado();

        assertThat(render()).contains("- / -").doesNotContain("null");
    }

    @Test
    void unNombreDeCursoConCaracteresEspecialesQuedaEscapadoEnElHtml() {
        Curso curso = Curso.builder().codigo("009-004").nombre("Curso <b>\"Especial\"</b> & Avanzado").horas(10)
                .costo(new BigDecimal("10")).cuposRestantes(1).cuposTotales(1)
                .modalidad(Modalidad.PRESENCIAL).estado(EstadoCurso.EN_ESPERA).build();
        when(cursoRepository.findAll()).thenReturn(List.of(curso));

        servicio.generarListado();

        assertThat(render()).doesNotContain("<b>\"Especial\"</b>").contains("&lt;b&gt;&quot;Especial&quot;&lt;/b&gt;");
    }

    @Test
    void seRenderizaUsandoElPdfQueDevuelvePdfRendererService() {
        when(cursoRepository.findAll()).thenReturn(List.of());
        byte[] esperado = new byte[]{5, 6, 7};
        when(pdfRendererService.renderizar(any())).thenReturn(esperado);

        assertThat(servicio.generarListado()).isSameAs(esperado);
    }
}
