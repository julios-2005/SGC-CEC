package com.upse.inscripciones.util;

/**
 * Escape de HTML centralizado para todo texto de origen externo (formulario
 * público de inscripción, datos ingresados por staff, etc.) que se interpola
 * dentro de HTML generado por el servidor: cuerpos de correo (EmailService)
 * y plantillas de PDF (ComprobanteMatriculaPdfService,
 * CoordinadorListadoPdfService, EspecialistaListadoPdfService,
 * CursoListadoPdfService).
 * <p>
 * Antes cada uno de esos servicios tenía su propia copia idéntica de este
 * método. La duplicación en sí no causaba bugs, pero sí fue la causa
 * concreta de que EmailService.construirCuerpoHtml() quedara sin escapar en
 * una revisión anterior (se aplicó el patrón en 3-4 lugares y se olvidó
 * uno). Centralizarlo aquí no elimina el riesgo de "olvidar llamarlo" en un
 * servicio nuevo, pero sí elimina el riesgo de que una futura corrección del
 * escape se aplique a mano en varios sitios y alguno quede desactualizado.
 * Por eso también escapa comillas simples y dobles (no solo &amp;, &lt; y
 * &gt;): ningún uso actual interpola texto dentro de un atributo HTML
 * citado, pero si un servicio nuevo lo hace, ya queda cubierto sin tener
 * que acordarse.
 * <p>
 * Para los PDF de listado NO se usa este método directamente: ListadoPdfHtml
 * lo aplica a todas las celdas por construcción, así que un listado nuevo no
 * puede olvidarse de escapar un campo.
 */
public final class HtmlEscapeUtils {

    private HtmlEscapeUtils() {
    }

    public static String escapar(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
