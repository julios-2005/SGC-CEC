package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import com.upse.inscripciones.util.ListadoPdfHtml;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PDF con los cursos a cargo de UN coordinador puntual. Usado por
 * consulta-coordinadores.html. El HTML lo arma ListadoPdfHtml, que escapa
 * todas las celdas.
 */
@Service
@RequiredArgsConstructor
public class CoordinadorListadoPdfService {

    private final CoordinadorRepository coordinadorRepository;
    private final PlanificacionRepository planificacionRepository;
    private final PdfRendererService pdfRendererService;

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] generarListadoPorCoordinador(Long idCoordinador) {
        Coordinador c = coordinadorRepository.findById(idCoordinador)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el coordinador con id " + idCoordinador));

        List<Planificacion> cursos = planificacionRepository.findByCoordinadorId(idCoordinador);

        List<List<Object>> filas = cursos.stream()
                .map(p -> java.util.Arrays.<Object>asList(
                        p.getCurso().getNombre(),
                        p.getCurso().getCodigo(),
                        p.getModalidad(),
                        p.getHorario(),
                        ListadoPdfHtml.rangoFechas(p.getFechaInicio(), p.getFechaFin()),
                        p.getNombresDocentes()))
                .toList();

        String html = ListadoPdfHtml.tabla(
                "Cursos a cargo de " + c.getNombres() + " " + c.getApellidos() + " (Cédula " + c.getCedula() + ")",
                List.of("Curso", "Código", "Modalidad", "Horario", "Fechas", "Especialista"),
                filas,
                "Este coordinador no tiene cursos a cargo.");

        return pdfRendererService.renderizar(html);
    }
}
