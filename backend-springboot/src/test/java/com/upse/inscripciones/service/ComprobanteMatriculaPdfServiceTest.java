package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Inscripcion;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.entity.TipoUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El comprobante de matrícula recibe datos que vienen originalmente del
 * formulario público de inscripción (sin autenticar), así que estas pruebas
 * fijan que el HTML que le llega a PdfRendererService tiene ese texto
 * escapado, y que el número de matrícula y los montos (costo, descuento,
 * total) se calculan y formatean correctamente. No se prueba la generación
 * real del PDF (eso ya lo cubre PdfRendererServiceTest): PdfRendererService
 * se mockea y se captura el HTML que se le pasa.
 */
class ComprobanteMatriculaPdfServiceTest {

    private DescuentoService descuentoService;
    private PdfRendererService pdfRendererService;
    private ComprobanteMatriculaPdfService servicio;

    @BeforeEach
    void setup() {
        descuentoService = mock(DescuentoService.class);
        pdfRendererService = mock(PdfRendererService.class);
        servicio = new ComprobanteMatriculaPdfService(descuentoService, pdfRendererService);
        when(pdfRendererService.renderizar(any())).thenReturn(new byte[]{1, 2, 3});
    }

    private static Especialista especialista() {
        return Especialista.builder().id(1L).nombres("Carlos").apellidos("Ruiz").build();
    }

    private static Curso curso(String nombre, BigDecimal costo) {
        return Curso.builder().idCurso(1L).nombre(nombre).codigo("009-001").horas(40)
                .costo(costo).modalidad(Modalidad.VIRTUAL).build();
    }

    private static Planificacion planificacion(Curso curso) {
        return Planificacion.builder().id(1L).curso(curso).coordinador(Coordinador.builder().id(1L).build())
                .especialista(especialista()).horario("Sábados 08h00-12h00")
                .fechaInicio(LocalDate.of(2026, 10, 1)).fechaFin(LocalDate.of(2026, 12, 19))
                .modalidad(Modalidad.VIRTUAL).build();
    }

    private static Inscripcion inscripcion(String nombreCompleto, String cedula, TipoUsuario tipo,
                                           Planificacion planificacion) {
        return Inscripcion.builder().id(42L).nombreCompleto(nombreCompleto).cedula(cedula)
                .tipoUsuario(tipo).planificacion(planificacion)
                .fechaRegistro(LocalDateTime.of(2026, 9, 20, 10, 30)).build();
    }

    @Test
    void elNumeroDeMatriculaSeFormateaConSeisDigitosConCerosALaIzquierda() {
        when(descuentoService.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        Planificacion p = planificacion(curso("Excel Avanzado", new BigDecimal("100.00")));

        servicio.generar(inscripcion("Ana Pérez", "0912345678", TipoUsuario.EXTERNO, p));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).contains("INS-000042");
    }

    @Test
    void elNombreYElCursoDelFormularioPublicoSeEscapanEnElHtml() {
        when(descuentoService.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        Planificacion p = planificacion(curso("Curso <script>alert(1)</script> & Cía", new BigDecimal("50")));

        servicio.generar(inscripcion("Juan <b>\"Peligroso\"</b> & Torres", "0912345678", TipoUsuario.EXTERNO, p));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).doesNotContain("<script>alert(1)</script>")
                .doesNotContain("<b>\"Peligroso\"</b>")
                .contains("&lt;script&gt;")
                .contains("&amp; C\u00eda")
                .contains("&amp; Torres");
    }

    @Test
    void elTotalSeCalculaRestandoElDescuentoAlCosto() {
        when(descuentoService.obtenerPorcentajePara(TipoUsuario.DOCENTE_UPSE)).thenReturn(new BigDecimal("20"));
        Planificacion p = planificacion(curso("Curso X", new BigDecimal("100.00")));

        servicio.generar(inscripcion("Ana Pérez", "0912345678", TipoUsuario.DOCENTE_UPSE, p));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        // 100 - 20% = 80.00; el 20% de descuento se muestra sin ceros decimales sobrantes
        assertThat(html.getValue()).contains("$ 100.00").contains("- $ 20.00").contains("$ 80.00")
                .contains("Descuento (20%)");
    }

    @Test
    void sinDescuentoElMontoDeDescuentoEsCeroYElTotalEsIgualAlCosto() {
        when(descuentoService.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        Planificacion p = planificacion(curso("Curso X", new BigDecimal("75.50")));

        servicio.generar(inscripcion("Ana Pérez", "0912345678", TipoUsuario.EXTERNO, p));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).contains("$ 75.50").contains("- $ 0.00").contains("$ 75.50");
    }

    @Test
    void laFechaDeExamenUsaLaFechaFinDeLaPlanificacionEnFormatoLargoEnEspanol() {
        when(descuentoService.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        Planificacion p = planificacion(curso("Curso X", new BigDecimal("50")));
        p.setFechaFin(LocalDate.of(2026, 10, 19));

        servicio.generar(inscripcion("Ana Pérez", "0912345678", TipoUsuario.EXTERNO, p));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(pdfRendererService).renderizar(html.capture());
        assertThat(html.getValue()).contains("19 de octubre de 2026");
    }

    @Test
    void elPdfGeneradoEsElQueDevuelvePdfRendererService() {
        when(descuentoService.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        byte[] esperado = new byte[]{9, 9, 9};
        when(pdfRendererService.renderizar(any())).thenReturn(esperado);
        Planificacion p = planificacion(curso("Curso X", new BigDecimal("50")));

        byte[] resultado = servicio.generar(inscripcion("Ana Pérez", "0912345678", TipoUsuario.EXTERNO, p));

        assertThat(resultado).isSameAs(esperado);
    }
}
