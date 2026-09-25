package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.repository.EspecialistaRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import com.upse.inscripciones.util.ListadoPdfHtml;
import com.upse.inscripciones.util.ListadoPdfHtml.Celda;
import com.upse.inscripciones.util.ListadoPdfHtml.ItemLista;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * PDF de especialistas (listado general y por especialista). El HTML lo arma
 * ListadoPdfHtml, que escapa todas las celdas.
 */
@Service
@RequiredArgsConstructor
public class EspecialistaListadoPdfService {

    private final EspecialistaRepository especialistaRepository;
    private final PlanificacionRepository planificacionRepository;
    private final PdfRendererService pdfRendererService;

    public byte[] generarListado() {
        List<Especialista> especialistas = especialistaRepository.findAll();

        List<List<Object>> filas = especialistas.stream().map(d -> {
            List<Planificacion> cursos = planificacionRepository.findByEspecialistaId(d.getId());
            Celda cursosCelda = cursos.isEmpty()
                    ? Celda.atenuada("Sin cursos dictados")
                    : Celda.lista(cursos.stream()
                            .map(p -> new ItemLista(p.getCurso().getNombre(),
                                    ListadoPdfHtml.rangoFechas(p.getFechaInicio(), p.getFechaFin())))
                            .toList());
            return java.util.Arrays.<Object>asList(
                    d.getApellidos() + " " + d.getNombres(),
                    d.getCedula(),
                    d.getEspecialidad(),
                    d.getAreaConocimiento(),
                    d.getEstado() != null && d.getEstado() ? "Activo" : "Disponible",
                    cursosCelda);
        }).toList();

        String html = ListadoPdfHtml.tabla(
                "Listado de especialistas y cursos dictados",
                List.of("Especialista", "Cédula", "Especialidad", "Área de conocimiento", "Estado", "Cursos dictados"),
                filas,
                "No hay especialistas registrados.");

        return pdfRendererService.renderizar(html);
    }

    // Para consulta-especialistas.html — PDF con solo los cursos de UN
    // especialista puntual, en formato tabla (una fila por curso).
    public byte[] generarListadoPorEspecialista(Long idEspecialista) {
        Especialista d = especialistaRepository.findById(idEspecialista)
                .orElseThrow(() -> new com.upse.inscripciones.exception.RecursoNoEncontradoException(
                        "No se encontró el especialista con id " + idEspecialista));

        List<Planificacion> cursos = planificacionRepository.findByEspecialistaId(idEspecialista);

        List<List<Object>> filas = cursos.stream()
                .map(p -> java.util.Arrays.<Object>asList(
                        p.getCurso().getNombre(),
                        p.getCurso().getCodigo(),
                        p.getModalidad(),
                        p.getHorario(),
                        ListadoPdfHtml.rangoFechas(p.getFechaInicio(), p.getFechaFin()),
                        p.getCostoEspecialista()))
                .toList();

        String html = ListadoPdfHtml.tabla(
                "Cursos dictados por " + d.getNombres() + " " + d.getApellidos() + " (Cédula " + d.getCedula() + ")",
                List.of("Curso", "Código", "Modalidad", "Horario", "Fechas", "Costo especialista"),
                filas,
                "Este especialista no tiene cursos dictados.");

        return pdfRendererService.renderizar(html);
    }
}
