package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InscripcionServiceTest {
    @Test
    void falloDelSegundoDocumentoLimpiaElPrimeroYNoGuardaLaInscripcion() {
        InscripcionRepository registrations = mock(InscripcionRepository.class);
        PlanificacionRepository plans = mock(PlanificacionRepository.class);
        CursoRepository courses = mock(CursoRepository.class);
        FileStorageService files = mock(FileStorageService.class);
        InscripcionService service = new InscripcionService(registrations, plans, courses, files,
                mock(ComprobanteMatriculaPdfService.class), mock(EmailService.class), mock(InformeEconomicoService.class));
        when(plans.findById(1L)).thenReturn(Optional.of(Planificacion.builder()
                .fechaFin(LocalDate.now().plusDays(1))
                .curso(Curso.builder().idCurso(1L).nombre("Prueba").estado(EstadoCurso.EN_ESPERA).build())
                .build()));
        when(courses.decrementarCupoSiHayDisponible(1L)).thenReturn(1);
        when(files.guardarArchivo(any(), eq("comprobantes-pago"), anyString(), anyString(), anyString())).thenReturn("comprobantes-pago/nuevo.pdf");
        when(files.guardarArchivo(any(), eq("copias-cedula"), anyString(), anyString(), anyString())).thenThrow(new ValidacionException("Documento inválido"));
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
        assertThatThrownBy(() -> service.registrarInscripcion(TipoUsuario.EXTERNO, "Persona de Prueba", 1L, " 1234567890 ",
                "+593991234567", "prueba@example.test", "Dirección", Sexo.FEMENINO, file, file, null)).isInstanceOf(ValidacionException.class);
        verify(files).eliminarSiExiste("comprobantes-pago/nuevo.pdf");
        verify(registrations).existsByCedulaAndPlanificacionId("1234567890", 1L);
        verify(registrations, never()).save(any());
    }

    @Test
    void recalculaAntesDeGenerarYEnviarElComprobanteDeAceptacion() {
        InscripcionRepository registrations = mock(InscripcionRepository.class);
        PlanificacionRepository plans = mock(PlanificacionRepository.class);
        CursoRepository courses = mock(CursoRepository.class);
        FileStorageService files = mock(FileStorageService.class);
        ComprobanteMatriculaPdfService pdf = mock(ComprobanteMatriculaPdfService.class);
        EmailService mail = mock(EmailService.class);
        InformeEconomicoService reports = mock(InformeEconomicoService.class);
        InscripcionService service = new InscripcionService(registrations, plans, courses, files, pdf, mail, reports);

        Curso course = Curso.builder().idCurso(1L).nombre("Curso de prueba")
                .estado(EstadoCurso.EN_PROCESO).cuposTotales(10).cuposRestantes(9).build();
        Planificacion plan = Planificacion.builder().id(2L).curso(course).build();
        Inscripcion registration = Inscripcion.builder().id(3L).planificacion(plan)
                .nombreCompleto("Estudiante de prueba").cedula("1234567890")
                .correoElectronico("estudiante@example.test").estado(EstadoInscripcion.PENDIENTE).build();
        when(registrations.findByIdForUpdate(3L)).thenReturn(Optional.of(registration));
        when(registrations.save(any(Inscripcion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pdf.generar(registration)).thenReturn(new byte[]{1, 2, 3});
        when(files.guardarBytes(any(), anyString(), anyString())).thenReturn("comprobantes/matricula.pdf");

        service.cambiarEstado(3L, EstadoInscripcion.ACEPTADA);

        var order = inOrder(reports, pdf, mail);
        order.verify(reports).recalcularYGuardar(2L);
        order.verify(pdf).generar(registration);
        order.verify(mail).enviarComprobanteMatricula(
                "estudiante@example.test", "Estudiante de prueba", "Curso de prueba", new byte[]{1, 2, 3});
    }
}
