package com.upse.inscripciones.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.regex.Pattern;

/**
 * Utilidad genérica para convertir una plantilla HTML (string, con estilos
 * inline o <style> embebido) en un PDF. La usan tanto el comprobante de
 * matrícula como el listado imprimible de especialistas, así evitamos construir
 * los PDF celda por celda con una librería de bajo nivel.
 */
@Service
public class PdfRendererService {

    // openhtmltopdf parsea el HTML como XML estricto: un "&" suelto (que no
    // forme parte de una entidad ya válida como &amp; o &#123;) rompe el
    // parser. Las plantillas de este proyecto se arman concatenando texto
    // libre (nombres de cursos, empresas, etc.) que puede traer "&" tal cual,
    // así que lo escapamos antes de renderizar en vez de exigirle a cada
    // llamador que lo haga.
    private static final Pattern AMPERSAND_SUELTO =
            Pattern.compile("&(?!(?:[a-zA-Z][a-zA-Z0-9]*|#[0-9]+|#x[0-9a-fA-F]+);)");

    public byte[] renderizar(String html) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            String htmlSaneado = AMPERSAND_SUELTO.matcher(html).replaceAll("&amp;");
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlSaneado, null);
            builder.toStream(salida);
            builder.run();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF: " + e.getMessage(), e);
        }
    }
}
