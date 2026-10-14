package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Inscripcion;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.util.HtmlEscapeUtils;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteMatriculaPdfService {

    private final DescuentoService descuentoService;
    private final PdfRendererService pdfRendererService;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Logo oficial UPSE (círculo con "UPSE" debajo) embebido como data URI
    // en base64, para que el PDF no dependa de rutas de archivo/red al
    // renderizarse (openhtmltopdf no tiene por qué tener acceso al
    // classpath vía URL relativa).
    private String logoBase64;

    @PostConstruct
    void cargarLogo() {
        try (InputStream in = new ClassPathResource("reports/icon-512.png").getInputStream()) {
            logoBase64 = Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            log.warn("No se pudo cargar el logo UPSE para el comprobante de matrícula: {}", e.getMessage());
            logoBase64 = null;
        }
    }

    public byte[] generar(Inscripcion inscripcion) {
        Planificacion planificacion = inscripcion.getPlanificacion();
        BigDecimal costoCurso = planificacion.getCurso().getCosto();
        BigDecimal porcentajeDescuento = descuentoService.obtenerPorcentajePara(inscripcion.getTipoUsuario());

        BigDecimal montoDescuento = costoCurso
                .multiply(porcentajeDescuento)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = costoCurso.subtract(montoDescuento);

        String numeroMatricula = "INS-" + String.format("%06d", inscripcion.getId());

        // La fecha del examen es la fecha fin de la planificación/curso al
        // que pertenece esta matrícula (no una fecha fija).
        LocalDate fechaExamen = planificacion.getFechaFin();
        String avisoImportante = "AVISO IMPORTANTE: La fecha prevista para la realización del examen es el "
                + formatearFechaLarga(fechaExamen)
                + " y la entrega del certificado correspondiente deberá efectuarse en un plazo máximo de quince (15) "
                + "días calendario posteriores a la presentación del informe del docente.";

        String logoHtml = logoBase64 != null
                ? "<img class=\"logo\" src=\"data:image/png;base64," + logoBase64 + "\" alt=\"UPSE\" />"
                : "";

        String html = """
                <html>
                <head>
                <meta charset="UTF-8" />
                <style>
                  body { font-family: Helvetica, Arial, sans-serif; font-size: 11.5px; color: #1c2430; }
                  .barra-superior { height: 6px; background: linear-gradient(to right, #0d2340 50%%, #f0c419 50%%); margin-bottom: 14px; }
                  .encabezado { text-align: center; margin-bottom: 18px; border-bottom: 3px solid #c9a227; padding-bottom: 10px; }
                  .encabezado .logo { width: 62px; height: 62px; display: block; margin: 0 auto 8px; }
                  .encabezado h1 { font-size: 15px; color: #0d2340; margin: 0 0 4px; }
                  .encabezado h2 { font-size: 13px; color: #0d2340; margin: 0; }
                  .encabezado h3 { font-size: 12.5px; color: #6b7280; margin: 6px 0 0; font-weight: normal; }
                  table.datos { width: 100%%; border-collapse: collapse; margin-bottom: 16px; }
                  table.datos td { border: 1px solid #e5e0d5; padding: 7px 10px; }
                  table.datos td.etiqueta { background: #f6f4ef; font-weight: bold; width: 32%%; color: #0d2340; }
                  table.curso { width: 100%%; border-collapse: collapse; margin-bottom: 12px; }
                  table.curso th { background: #0d2340; color: #fff; padding: 8px 10px; text-align: left; font-size: 10.5px; text-transform: uppercase; }
                  table.curso td { border-bottom: 1px solid #e5e0d5; padding: 8px 10px; }
                  .totales { width: 100%%; margin-top: 6px; }
                  .totales td { padding: 5px 10px; text-align: right; }
                  .totales td.etq { color: #6b7280; }
                  .totales tr.total td { font-weight: bold; font-size: 13.5px; color: #0d2340; border-top: 2px solid #0d2340; }
                  .aviso { margin-top: 18px; padding: 10px 12px; background: #fff8e1; border: 1px solid #f0c419; border-left: 4px solid #f0c419; font-size: 10px; color: #5a4a00; }
                  .aviso b { color: #0d2340; }
                  .barra-inferior { height: 6px; background: linear-gradient(to right, #0d2340 50%%, #f0c419 50%%); margin-top: 22px; }
                  .pie { margin-top: 10px; font-size: 9.5px; color: #6b7280; text-align: center; }
                </style>
                </head>
                <body>
                  <div class="barra-superior"></div>
                  <div class="encabezado">
                    %s
                    <h1>UNIVERSIDAD ESTATAL PENÍNSULA DE SANTA ELENA</h1>
                    <h2>Centro de Educación Continua</h2>
                    <h3>Comprobante de Matrícula</h3>
                  </div>

                  <table class="datos">
                    <tr><td class="etiqueta">Estudiante</td><td>%s</td></tr>
                    <tr><td class="etiqueta">Identificación</td><td>%s</td></tr>
                    <tr><td class="etiqueta"># Matrícula</td><td>%s</td></tr>
                    <tr><td class="etiqueta">Fecha Matrícula</td><td>%s</td></tr>
                    <tr><td class="etiqueta">Tipo Matrícula</td><td>%s</td></tr>
                  </table>

                  <table class="curso">
                    <thead>
                      <tr><th>Curso — Modalidad</th><th>Especialista</th><th>Horario</th><th>Horas</th></tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>%s — %s</td>
                        <td>%s</td>
                        <td>%s (%s – %s)</td>
                        <td>%s</td>
                      </tr>
                    </tbody>
                  </table>

                  <table class="totales">
                    <tr><td class="etq">Costo Matrícula</td><td>$ %s</td></tr>
                    <tr><td class="etq">Descuento (%s%%)</td><td>- $ %s</td></tr>
                    <tr class="total"><td>Valor Total</td><td>$ %s</td></tr>
                  </table>

                  <p class="aviso"><b>%s</b></p>

                  <div class="barra-inferior"></div>
                  <p class="pie">Documento generado automáticamente por el sistema de inscripciones del Centro de Educación Continua - UPSE.</p>
                </body>
                </html>
                """.formatted(
                logoHtml,
                HtmlEscapeUtils.escapar(inscripcion.getNombreCompleto()),
                HtmlEscapeUtils.escapar(inscripcion.getCedula()),
                numeroMatricula,
                inscripcion.getFechaRegistro() != null ? inscripcion.getFechaRegistro().format(FECHA_HORA) : "-",
                HtmlEscapeUtils.escapar(inscripcion.getTipoUsuario().getDescripcion()),
                HtmlEscapeUtils.escapar(planificacion.getCurso().getNombre()),
                HtmlEscapeUtils.escapar(planificacion.getModalidad().name()),
                HtmlEscapeUtils.escapar(planificacion.getNombresDocentes()),
                HtmlEscapeUtils.escapar(planificacion.getHorario()),
                planificacion.getFechaInicio().format(FECHA),
                planificacion.getFechaFin().format(FECHA),
                planificacion.getCurso().getHoras(),
                costoCurso.setScale(2, RoundingMode.HALF_UP),
                porcentajeDescuento.stripTrailingZeros().toPlainString(),
                montoDescuento,
                total,
                HtmlEscapeUtils.escapar(avisoImportante)
        );

        return pdfRendererService.renderizar(html);
    }

    private static final String[] MESES = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    };

    /** Ej: 19 de octubre de 2026 */
    private String formatearFechaLarga(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }
}
