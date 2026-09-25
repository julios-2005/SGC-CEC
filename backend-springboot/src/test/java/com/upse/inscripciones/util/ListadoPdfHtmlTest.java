package com.upse.inscripciones.util;

import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.service.PdfRendererService;
import com.upse.inscripciones.util.ListadoPdfHtml.Celda;
import com.upse.inscripciones.util.ListadoPdfHtml.ItemLista;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El objetivo de ListadoPdfHtml es que NINGÚN dato llegue sin escapar al HTML
 * del PDF, sin que quien arma el listado tenga que acordarse de hacerlo.
 */
class ListadoPdfHtmlTest {

    private static final String ATAQUE = "<script>alert('x')</script><img src=x onerror=1>\"&";

    @Test
    void escapaTodaCeldaDeTextoAunqueQuienLlamaNoHagaNada() {
        String html = ListadoPdfHtml.tabla("Sub", List.of("A", "B"),
                List.of(List.of(ATAQUE, 42)), "vacío");

        assertThat(html).doesNotContain("<script>").doesNotContain("<img");
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
        assertThat(html).contains("<td>42</td>");
    }

    @Test
    void escapaSubtituloYEncabezados() {
        String html = ListadoPdfHtml.tabla("<b>" + ATAQUE, List.of("<i>Col</i>"),
                List.of(List.of("x")), "vacío");

        assertThat(html).doesNotContain("<b>").doesNotContain("<i>").doesNotContain("<script>");
        assertThat(html).contains("<th>&lt;i&gt;Col&lt;/i&gt;</th>");
    }

    @Test
    void escapaElMensajeDeSinFilas() {
        String html = ListadoPdfHtml.tabla("Sub", List.of("A", "B", "C"), List.of(), "<u>nada</u>");

        assertThat(html).contains("<td colspan='3' class='sin-filas'>&lt;u&gt;nada&lt;/u&gt;</td>");
        assertThat(html).doesNotContain("<u>");
    }

    @Test
    void nuloSeMuestraComoGuionYLosEnumsPorSuNombre() {
        String html = ListadoPdfHtml.tabla("Sub", List.of("A", "B"),
                List.of(Arrays.asList(null, Modalidad.values()[0])), "vacío");

        assertThat(html).contains("<td>-</td>");
        assertThat(html).contains("<td>" + Modalidad.values()[0].name() + "</td>");
    }

    @Test
    void lasCeldasConEstructuraTambienEscapanSuTexto() {
        Celda lista = Celda.lista(List.of(new ItemLista(ATAQUE, "<b>detalle</b>"), new ItemLista("Curso", null)));
        Celda atenuada = Celda.atenuada(ATAQUE);

        String html = ListadoPdfHtml.tabla("Sub", List.of("A", "B"), List.of(List.of(lista, atenuada)), "vacío");

        assertThat(html).doesNotContain("<script>").doesNotContain("<img").doesNotContain("<b>");
        assertThat(html).contains("<ul class='lista-cursos'>");
        assertThat(html).contains("<span class='fechas'>(&lt;b&gt;detalle&lt;/b&gt;)</span>");
        assertThat(html).contains("<li>Curso</li>");
    }

    @Test
    void unaFilaConOtroNumeroDeCeldasSeRechaza() {
        assertThatThrownBy(() -> ListadoPdfHtml.tabla("Sub", List.of("A", "B"),
                List.of(List.of("solo una")), "vacío"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangoDeFechasToleraFechasFaltantes() {
        assertThat(ListadoPdfHtml.rangoFechas(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 2, 6)))
                .isEqualTo("05/01/2026 – 06/02/2026");
        assertThat(ListadoPdfHtml.rangoFechas(null, null)).isEqualTo("- – -");
    }

    @Test
    void elHtmlGeneradoEsUnXhtmlValidoQueSeConvierteEnPdf() {
        Celda lista = Celda.lista(List.of(new ItemLista(ATAQUE, "05/01/2026 – 06/02/2026")));
        String html = ListadoPdfHtml.tabla(ATAQUE, List.of("A", "B", "C"),
                List.of(Arrays.asList(ATAQUE, null, lista)), "vacío");

        byte[] pdf = new PdfRendererService().renderizar(html);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
