package com.upse.inscripciones.service;

import com.upse.inscripciones.util.HtmlEscapeUtils;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.remitente-nombre}")
    private String remitenteNombre;

    @Value("${spring.mail.username}")
    private String remitenteEmail;

    /**
     * Envía el comprobante de matrícula en PDF al correo del estudiante tras
     * ser aceptado. Si el envío falla (credenciales SMTP no configuradas,
     * sin internet, etc.) NO se interrumpe el flujo de aceptación: el PDF
     * ya quedó guardado en el sistema y el staff puede reenviarlo o
     * descargarlo manualmente.
     * <p>
     * El cuerpo del correo va en HTML e incluye, además de la confirmación
     * de matrícula, las Políticas del Curso y las Recomendaciones/Políticas
     * del Examen. Antes esto se enviaba aparte por WhatsApp; ahora queda
     * todo en un solo correo para que el estudiante tenga constancia
     * escrita desde el primer contacto.
     * <p>
     * Nota (Async): corre en un hilo aparte (ver AsyncConfig) para que
     * InscripcionService.cambiarEstado() no se quede esperando a que Gmail
     * responda antes de devolverle la respuesta al administrador. Solo
     * recibe datos ya resueltos (String/byte[], nada de entidades JPA), a
     * propósito: un método asíncrono no debe depender de que la
     * transacción de quien lo llamó siga abierta.
     */
    @Async
    public void enviarComprobanteMatricula(String correoDestino, String nombreCompleto,
                                            String nombreCurso, byte[] pdf) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
            helper.setFrom(remitenteEmail, remitenteNombre);
            helper.setTo(correoDestino);
            helper.setSubject("Comprobante de Matrícula - " + nombreCurso);

            // Multipart explícito: HTML y PDF quedan como partes hermanas
            // del multipart/mixed. Esto evita que el cuerpo HTML quede
            // escondido dentro de un multipart/related o multipart/alternative.
            Multipart mixed = new MimeMultipart("mixed");
            BodyPart html = new MimeBodyPart();
            html.setContent(construirCuerpoHtml(
                    HtmlEscapeUtils.escapar(nombreCompleto),
                    HtmlEscapeUtils.escapar(nombreCurso)), "text/html; charset=UTF-8");
            mixed.addBodyPart(html);

            BodyPart attachment = new MimeBodyPart();
            attachment.setFileName("comprobante-matricula.pdf");
            attachment.setContent(pdf, "application/pdf");
            mixed.addBodyPart(attachment);

            mensaje.setContent(mixed);
            mensaje.saveChanges();
            mailSender.send(mensaje);
        } catch (Exception e) {
            // Se registra en el logger pero no se lanza: la aceptación de la
            // inscripción ya se guardó correctamente en la base de datos.
            log.error("No se pudo enviar el correo con el comprobante de matrícula a {}: {}",
                    correoDestino, e.getMessage(), e);
        }
    }

    /**
     * Arma el cuerpo HTML del correo de aceptación: confirmación de
     * matrícula + Políticas del Curso + Políticas/Recomendaciones del
     * Examen. El texto de ambos bloques es fijo (genérico para todos los
     * cursos); si en el futuro necesitan variar por curso, conviene
     * moverlo a una plantilla o a un campo configurable en Curso.
     * <p>
     * IMPORTANTE: este método asume que {@code nombreCompleto} y
     * {@code nombreCurso} ya vienen escapados (ver
     * {@link HtmlEscapeUtils#escapar(String)} en el único punto de llamada,
     * {@link #enviarComprobanteMatricula}). Ambos valores pueden originarse
     * en el formulario público de inscripción, sin autenticar, así que
     * insertarlos sin escapar en un correo HTML permitiría inyectar
     * enlaces/markup falsos en un correo institucional legítimo. Si se
     * agrega una nueva llamada a este método, escapar ahí también.
     */
    private String construirCuerpoHtml(String nombreCompleto, String nombreCurso) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#222;max-width:640px;margin:0 auto;\">"

                + "<p>Estimado(a) <strong>" + nombreCompleto + "</strong>,</p>"

                + "<p>Su inscripción al curso <strong>\"" + nombreCurso + "\"</strong> ha sido "
                + "<strong style=\"color:#1a7a1a;\">ACEPTADA</strong>. Adjuntamos su comprobante "
                + "de matrícula en PDF.</p>"

                + "<hr style=\"border:none;border-top:1px solid #ddd;margin:24px 0;\">"

                + "<h2 style=\"color:#c0392b;font-size:18px;\">Políticas del Curso</h2>"
                + "<p>Este es un curso de <strong>APROBACIÓN</strong>, por lo tanto:</p>"
                + "<ol style=\"padding-left:20px;line-height:1.6;\">"
                + "<li>La entrega y la evaluación de las tareas se llevará a cabo exclusivamente "
                + "a través de la <strong>plataforma virtual Moodle</strong>.</li>"
                + "<li>La <strong>asistencia mínima requerida</strong> para aprobar es del "
                + "<strong>70% del total de las sesiones</strong>.</li>"
                + "<li>Es obligatorio <strong>permanecer con la cámara encendida</strong> durante "
                + "todas las sesiones virtuales.</li>"
                + "<li>Al concluir el curso, <strong>las calificaciones estarán subidas en el aula "
                + "virtual</strong>. Si surge alguna novedad, infórmela lo antes posible al "
                + "especialista o coordinador(a) delegado.</li>"
                + "<li><strong>Cualquier forma de deshonestidad académica</strong> (plagio, "
                + "suplantación de identidad o falta de integridad) <strong>implica la pérdida "
                + "inmediata del curso</strong>.</li>"
                + "<li><strong>No existe examen de recuperación.</strong></li>"
                + "</ol>"
                + "<p style=\"background:#f5f5f5;padding:10px 14px;border-radius:6px;\">"
                + "<strong>Nota:</strong> Los participantes que alcancen la calificación mínima de "
                + "<strong>70 puntos</strong> recibirán el certificado de aprobación correspondiente "
                + "a través del correo electrónico registrado.</p>"

                + "<h2 style=\"color:#1a2f4b;font-size:18px;margin-top:28px;\">Políticas del Examen "
                + "— Recomendaciones</h2>"
                + "<ul style=\"padding-left:20px;line-height:1.6;\">"
                + "<li><strong>Conexión:</strong> usa preferiblemente internet por cable o ubícate "
                + "cerca del router. Si tu conexión falla, no podremos asistirte durante el examen.</li>"
                + "<li><strong>Equipo:</strong> realiza la prueba en computadora o laptop funcional. "
                + "Desde celular no se garantiza el correcto funcionamiento de la plataforma.</li>"
                + "<li><strong>Monitoreo:</strong> el monitoreo es parte del proceso. Si tu cámara o "
                + "audio no funcionan, tu examen no será válido.</li>"
                + "<li><strong>Interferencias:</strong> busca un espacio silencioso y sin personas "
                + "alrededor. Cualquier interferencia puede afectar el monitoreo y la continuidad de "
                + "tu prueba.</li>"
                + "<li><strong>Navegación:</strong> no minimices, abras nuevas pestañas o cambies de "
                + "aplicación. El sistema registra esas acciones y podría invalidar tu examen.</li>"
                + "<li><strong>Identificación:</strong> mantén tu documento de identidad a la mano "
                + "(cédula o pasaporte). Será requerido para validar tu identidad antes o durante el "
                + "examen.</li>"
                + "</ul>"
                + "<p style=\"background:#fff3cd;padding:10px 14px;border-radius:6px;border:1px solid "
                + "#ffe08a;\"><strong>Importante:</strong> si alguna de estas indicaciones no se "
                + "cumple y se presenta un inconveniente durante el examen, <strong>no habrá forma de "
                + "brindarte asistencia ni repetir la evaluación</strong>. Por favor, sigue todas las "
                + "instrucciones para evitar inconvenientes.</p>"

                + "<hr style=\"border:none;border-top:1px solid #ddd;margin:24px 0;\">"

                + "<p style=\"color:#555;font-size:13px;\">Centro de Educación Continua - UPSE</p>"
                + "</div>";
    }
}
