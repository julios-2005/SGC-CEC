package com.upse.inscripciones.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfRendererService convierte HTML a PDF (usada por los comprobantes y los
 * listados imprimibles). Estas pruebas fijan que produce bytes de PDF
 * válidos y que un HTML mal formado no cuelga el servicio, sino que falla
 * con una excepción clara.
 */
class PdfRendererServiceTest {

    private final PdfRendererService servicio = new PdfRendererService();

    @Test
    void unHtmlSimpleProduceBytesQueEmpiezanConLaFirmaDePdf() {
        byte[] pdf = servicio.renderizar("<html><body><h1>Hola</h1></body></html>");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void unHtmlConTildesYSimbolosSeRenderizaSinLanzarError() {
        byte[] pdf = servicio.renderizar(
                "<html><body><p>Educación & Formación — \"Especial\"</p></body></html>");

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void unHtmlIrrecuperablementeMalFormadoLanzaUnaExcepcionClaraEnVezDeColgarse() {
        assertThatThrownBy(() -> servicio.renderizar(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo generar el PDF");
    }

    @Test
    void unHtmlVacioNoLanzaExcepcionYProduceUnPdfMinimo() {
        byte[] pdf = servicio.renderizar("<html><body></body></html>");

        assertThat(pdf).isNotEmpty();
    }
}
