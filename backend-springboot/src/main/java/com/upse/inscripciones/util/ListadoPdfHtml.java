package com.upse.inscripciones.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Arma el HTML de los PDF de listado (una tabla con título institucional,
 * subtítulo y encabezados) para pasárselo a PdfRendererService.
 * <p>
 * Existe para que escapar el HTML no dependa de la memoria de quien escribe
 * un listado nuevo: quien llama entrega DATOS (texto plano, números, enums,
 * fechas ya formateadas) y este builder escapa TODO al construir el HTML.
 * No existe ninguna vía para inyectar HTML crudo a propósito: si algún día
 * hace falta un tipo de celda nuevo, se agrega aquí (con su escape) en lugar
 * de armar etiquetas en cada servicio.
 * <p>
 * Valores de celda admitidos: {@code null} (se muestra "-"), {@link Enum}
 * (su name()), {@link Celda} (creada con las fábricas de esta clase) o
 * cualquier otro objeto, del que se usa toString().
 */
public final class ListadoPdfHtml {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VACIO = "-";

    private ListadoPdfHtml() {
    }

    /** Elemento de una lista dentro de una celda: texto principal + detalle atenuado opcional. */
    public record ItemLista(String texto, String detalle) {
    }

    /** Celda con estructura propia. Su HTML lo genera esta clase, siempre con el texto ya escapado. */
    public static final class Celda {
        private final String html;

        private Celda(String html) {
            this.html = html;
        }

        /** Texto gris cursiva, para "Sin cursos dictados" y similares. */
        public static Celda atenuada(String texto) {
            return new Celda("<span class='sin-cursos'>" + HtmlEscapeUtils.escapar(texto) + "</span>");
        }

        /** Lista con viñetas; cada elemento puede llevar un detalle pequeño entre paréntesis. */
        public static Celda lista(List<ItemLista> items) {
            StringBuilder sb = new StringBuilder("<ul class='lista-cursos'>");
            for (ItemLista item : items) {
                sb.append("<li>").append(HtmlEscapeUtils.escapar(item.texto()));
                if (item.detalle() != null && !item.detalle().isBlank()) {
                    sb.append(" <span class='fechas'>(").append(HtmlEscapeUtils.escapar(item.detalle())).append(")</span>");
                }
                sb.append("</li>");
            }
            return new Celda(sb.append("</ul>").toString());
        }
    }

    /** "dd/MM/yyyy – dd/MM/yyyy"; una fecha faltante se muestra como "-". */
    public static String rangoFechas(LocalDate inicio, LocalDate fin) {
        return (inicio != null ? inicio.format(FECHA) : VACIO) + " – " + (fin != null ? fin.format(FECHA) : VACIO);
    }

    /**
     * @param subtitulo        texto plano bajo el título institucional
     * @param encabezados      texto plano de cada columna
     * @param filas            una lista de celdas por fila, del mismo largo que encabezados
     * @param mensajeSinFilas  texto plano que se muestra si no hay filas
     * @throws IllegalArgumentException si alguna fila no tiene tantas celdas como encabezados
     */
    public static String tabla(String subtitulo, List<String> encabezados,
                               List<? extends List<?>> filas, String mensajeSinFilas) {
        StringBuilder cabecera = new StringBuilder("<tr>");
        for (String encabezado : encabezados) {
            cabecera.append("<th>").append(HtmlEscapeUtils.escapar(encabezado)).append("</th>");
        }
        cabecera.append("</tr>");

        StringBuilder cuerpo = new StringBuilder();
        if (filas.isEmpty()) {
            cuerpo.append("<tr><td colspan='").append(encabezados.size()).append("' class='sin-filas'>")
                    .append(HtmlEscapeUtils.escapar(mensajeSinFilas)).append("</td></tr>");
        }
        for (List<?> fila : filas) {
            if (fila.size() != encabezados.size()) {
                throw new IllegalArgumentException("Cada fila debe tener " + encabezados.size()
                        + " celdas pero tiene " + fila.size());
            }
            cuerpo.append("<tr>");
            for (Object valor : fila) {
                cuerpo.append("<td>").append(celda(valor)).append("</td>");
            }
            cuerpo.append("</tr>");
        }

        return """
                <html>
                <head>
                <meta charset="UTF-8" />
                <style>
                  body { font-family: Helvetica, Arial, sans-serif; font-size: 11px; color: #1c2430; }
                  h1 { font-size: 16px; color: #0d2340; margin-bottom: 2px; }
                  p.sub { color: #6b7280; margin-top: 0; margin-bottom: 16px; }
                  table { width: 100%%; border-collapse: collapse; }
                  th { background: #0d2340; color: #fff; text-align: left; padding: 6px 8px; font-size: 10px; text-transform: uppercase; }
                  td { border-bottom: 1px solid #e5e0d5; padding: 6px 8px; vertical-align: top; }
                  ul.lista-cursos { margin: 0; padding-left: 14px; }
                  .fechas { color: #6b7280; font-size: 9.5px; }
                  .sin-cursos { color: #6b7280; font-style: italic; }
                  .sin-filas { color: #6b7280; font-style: italic; text-align: center; }
                </style>
                </head>
                <body>
                  <h1>UPSE — Centro de Educación Continua</h1>
                  <p class="sub">%s</p>
                  <table>
                    <thead>
                      %s
                    </thead>
                    <tbody>
                      %s
                    </tbody>
                  </table>
                </body>
                </html>
                """.formatted(HtmlEscapeUtils.escapar(subtitulo), cabecera, cuerpo);
    }

    private static String celda(Object valor) {
        if (valor == null) return VACIO;
        if (valor instanceof Celda celda) return celda.html;
        if (valor instanceof Enum<?> e) return HtmlEscapeUtils.escapar(e.name());
        return HtmlEscapeUtils.escapar(valor.toString());
    }
}
