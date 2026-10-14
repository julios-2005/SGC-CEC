package com.upse.inscripciones.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmailService envía el comprobante de matrícula por correo. Lo crítico a
 * probar: el nombre y el curso —que llegan del formulario público, sin
 * autenticar— se escapan antes de insertarse en el HTML del correo (si no,
 * alguien podría inyectar markup/enlaces falsos en un correo institucional
 * legítimo, ver el comentario de EmailService#construirCuerpoHtml), y que un
 * fallo al enviar no interrumpa el flujo de aceptación (no lanza excepción).
 */
class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService servicio;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
        servicio = new EmailService(mailSender);
        ReflectionTestUtils.setField(servicio, "remitenteNombre", "CEC UPSE");
        ReflectionTestUtils.setField(servicio, "remitenteEmail", "cec@upse.edu.ec");
    }

    private static MimeMessage nuevoMensaje() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private static String parteHtml(MimeMessage mensaje) throws Exception {
        Multipart multipart = (Multipart) mensaje.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart parte = multipart.getBodyPart(i);
            if (parte.isMimeType("text/html")) {
                return (String) parte.getContent();
            }
        }
        throw new AssertionError("El correo no tiene una parte text/html");
    }

    @Test
    void escapaElNombreYElCursoAntesDeInsertarlosEnElHtmlDelCorreo() throws Exception {
        MimeMessage mensaje = nuevoMensaje();
        when(mailSender.createMimeMessage()).thenReturn(mensaje);

        servicio.enviarComprobanteMatricula("ana@ejemplo.com",
                "<script>alert(1)</script>", "Curso \"X\" & Y", new byte[] {1, 2, 3});

        verify(mailSender).send(mensaje);
        String html = parteHtml(mensaje);
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).contains("Curso &quot;X&quot; &amp; Y");
    }

    @Test
    void elCorreoIncluyeElRemitenteElDestinatarioYElAdjuntoEnPdf() throws Exception {
        MimeMessage mensaje = nuevoMensaje();
        when(mailSender.createMimeMessage()).thenReturn(mensaje);

        servicio.enviarComprobanteMatricula("ana@ejemplo.com", "Ana Perez", "Diplomado en X", new byte[] {1, 2, 3});

        assertThat(mensaje.getAllRecipients()[0].toString()).isEqualTo("ana@ejemplo.com");
        assertThat(mensaje.getFrom()[0].toString()).contains("cec@upse.edu.ec");
        assertThat(mensaje.getSubject()).contains("Diplomado en X");

        Multipart multipart = (Multipart) mensaje.getContent();
        boolean tieneAdjuntoPdf = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            if ("comprobante-matricula.pdf".equals(multipart.getBodyPart(i).getFileName())) {
                tieneAdjuntoPdf = true;
            }
        }
        assertThat(tieneAdjuntoPdf).isTrue();
    }

    @Test
    void elCuerpoIncluyeLasPoliticasDelCursoYDelExamen() throws Exception {
        MimeMessage mensaje = nuevoMensaje();
        when(mailSender.createMimeMessage()).thenReturn(mensaje);

        servicio.enviarComprobanteMatricula("ana@ejemplo.com", "Ana Perez", "Diplomado en X", new byte[] {1});

        String html = parteHtml(mensaje);
        assertThat(html).contains("Políticas del Curso");
        assertThat(html).contains("70% del total de las sesiones");
        assertThat(html).contains("Políticas del Examen");
        assertThat(html).contains("No existe examen de recuperación.");
    }

    @Test
    void unFalloAlEnviarSeRegistraPeroNoInterrumpeElFlujoDeAceptacion() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP no configurado"));

        // No debe lanzar: la inscripción ya quedó aceptada en la base de datos.
        servicio.enviarComprobanteMatricula("ana@ejemplo.com", "Ana Perez", "Diplomado en X", new byte[] {1});
    }

    @Test
    void unDestinatarioInvalidoNoLanzaExcepcionYNoLlegaAEnviar() {
        when(mailSender.createMimeMessage()).thenReturn(nuevoMensaje());

        // Un correo nulo hace fallar la construcción del mensaje (MimeMessageHelper.setTo);
        // eso también debe quedar contenido dentro del catch, sin propagar la excepción.
        servicio.enviarComprobanteMatricula(null, "Ana Perez", "Diplomado en X", new byte[] {1});

        verify(mailSender, org.mockito.Mockito.never()).send(any(MimeMessage.class));
    }
}
