package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.repository.CursoRepository;
import com.upse.inscripciones.util.ListadoPdfHtml;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Genera el PDF de "Imprimir listado" de cursos.html. El HTML lo arma
 * ListadoPdfHtml, que escapa todas las celdas.
 */
@Service
@RequiredArgsConstructor
public class CursoListadoPdfService {

    private final CursoRepository cursoRepository;
    private final PdfRendererService pdfRendererService;

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] generarListado() {
        List<Curso> cursos = cursoRepository.findAll();

        List<List<Object>> filas = cursos.stream()
                .map(c -> java.util.Arrays.<Object>asList(
                        c.getCodigo(),
                        c.getNombre(),
                        c.getHoras(),
                        c.getCosto() != null ? "$ " + c.getCosto() : null,
                        (c.getCuposRestantes() != null ? c.getCuposRestantes() : "-")
                                + " / " + (c.getCuposTotales() != null ? c.getCuposTotales() : "-"),
                        c.getModalidad(),
                        c.getEstado()))
                .toList();

        String html = ListadoPdfHtml.tabla(
                "Listado de cursos — generado " + java.time.LocalDateTime.now().format(FECHA_HORA),
                List.of("Código", "Nombre", "Horas", "Costo", "Cupos (restantes/totales)", "Modalidad", "Estado"),
                filas,
                "No hay cursos registrados.");

        return pdfRendererService.renderizar(html);
    }
}
