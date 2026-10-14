package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.CursoRequest;
import com.upse.inscripciones.dto.CursoResponse;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.service.CursoService;
import com.upse.inscripciones.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cursos")
@RequiredArgsConstructor
/*
 * Nota de auditoría (decisión de negocio, no un olvido): a diferencia de
 * CoordinadorController/UsuarioController, este controller NO restringe
 * ninguno de sus endpoints a ADMIN_GENERAL. Se confirmó que esto es
 * intencional — el frontend (app-compartida.js) muestra la sección de
 * Cursos a ambos roles (ADMIN_GENERAL y COORDINADOR) por igual, y solo
 * "Gestión de Usuarios" queda restringida a ADMIN_GENERAL. Cualquier
 * COORDINADOR con sesión activa puede hoy crear/editar/eliminar cualquier
 * curso del sistema, no solo los que coordina (no existe el concepto de
 * "mis cursos" en el modelo de permisos actual). Si esto cambia, aplicar
 * el mismo patrón @PreAuthorize("hasRole('ADMIN_GENERAL')") que ya usan
 * CoordinadorController y UsuarioController.
 */
public class CursoController {

    private final CursoService cursoService;
    private final FileStorageService fileStorageService;
    private final com.upse.inscripciones.service.ExcelTablaExportService excelTablaExportService;
    private final com.upse.inscripciones.service.CursoListadoPdfService cursoListadoPdfService;

    // ── GET /api/cursos ──────────────────────────────────────────────────────
    // Listado paginado para la pantalla de gestión (cursos.html). Parámetros
    // opcionales: texto (nombre o código), estado, page/size/sort.
    // Por defecto: 20 por página, ordenado por nombre.
    @GetMapping
    public ResponseEntity<Page<CursoResponse>> listarTodos(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String modalidad,
            @PageableDefault(size = 20, sort = "fechaRegistro", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(cursoService.listarTodos(
                texto, parseEstadoOpcional(estado), parseModalidadOpcional(modalidad), pageable));
    }

    // ── GET /api/cursos/listado-pdf ───────────────────────────────────────────
    // Genera un PDF descargable con todos los cursos (igual patrón que
    // /api/especialistas/listado-pdf).
    @GetMapping("/listado-pdf")
    public ResponseEntity<byte[]> listadoPdf() {
        byte[] pdf = cursoListadoPdfService.generarListado();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"listado-cursos.pdf\"")
                .body(pdf);
    }

    // ── GET /api/cursos/exportar ──────────────────────────────────────────────
    // Descarga un .xlsx con el listado de cursos (mismos filtros que /api/cursos,
    // pero sin paginar: exporta TODAS las filas que coincidan).
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String modalidad) throws java.io.IOException {

        List<CursoResponse> cursos = cursoService.listarParaExportar(
                texto, parseEstadoOpcional(estado), parseModalidadOpcional(modalidad));

        if (cursos.isEmpty()) {
            throw new ValidacionException("No hay cursos que coincidan con los filtros para exportar.");
        }

        List<com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel<CursoResponse>> columnas = List.of(
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Código", CursoResponse::getCodigo),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Nombre", CursoResponse::getNombre),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Horas", CursoResponse::getHoras),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.moneda("Costo", CursoResponse::getCosto),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Cupos totales", CursoResponse::getCuposTotales),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Cupos restantes", CursoResponse::getCuposRestantes),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Modalidad", c -> c.getModalidad() != null ? c.getModalidad().name() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Estado", c -> c.getEstado() != null ? c.getEstado().name() : null)
        );

        byte[] excel = excelTablaExportService.generar("Cursos", columnas, cursos);
        String nombreArchivo = "cursos-" + java.time.LocalDate.now() + ".xlsx";
        return com.upse.inscripciones.util.DescargaExcelUtil.responder(excel, nombreArchivo);
    }

    // ── GET /api/cursos/estado/{estado} ──────────────────────────────────────
    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<CursoResponse>> listarPorEstado(@PathVariable String estado) {
        return ResponseEntity.ok(cursoService.listarPorEstado(parseEstado(estado)));
    }

    // ── GET /api/cursos/{id} ──────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<CursoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(cursoService.obtenerPorId(id));
    }

    // ── POST /api/cursos ──────────────────────────────────────────────────────
    // multipart/form-data porque opcionalmente incluye la foto del curso.
    // @Valid sobre CursoRequest cubre nombre/código/horas/costo/cuposTotales;
    // modalidad y estado se siguen normalizando a mano (ver parseModalidad/
    // parseEstadoOpcional) porque su binding directo a enum es sensible a
    // mayúsculas y no da los mismos mensajes de error en español.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CursoResponse> registrar(@Valid CursoRequest request) {
        CursoResponse response = cursoService.guardarFormulario(null, request,
                parseModalidad(request.getModalidad()), parseEstadoOpcional(request.getEstado()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── PUT /api/cursos/{id} ──────────────────────────────────────────────────
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, path = "/{id}")
    public ResponseEntity<CursoResponse> actualizar(@PathVariable Long id, @Valid CursoRequest request) {
        return ResponseEntity.ok(cursoService.guardarFormulario(id, request,
                parseModalidad(request.getModalidad()), parseEstadoOpcional(request.getEstado())));
    }

    // ── PATCH /api/cursos/{id}/estado ────────────────────────────────────────
    @PatchMapping("/{id}/estado")
    public ResponseEntity<CursoResponse> cambiarEstado(
            @PathVariable Long id,
            @RequestParam String estado) {
        return ResponseEntity.ok(cursoService.cambiarEstado(id, parseEstado(estado)));
    }

    // ── DELETE /api/cursos/{id} ───────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        cursoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // Este endpoint es público (ver SecurityConfig), así que solo
    // puede servir fotos de cursos, nunca documentos de inscripción.
    private static final List<String> SUBCARPETAS_PERMITIDAS = List.of("fotos-cursos");

    // ── GET /api/cursos/archivos/fotos-cursos/{archivo} ───────────────────────
    @GetMapping("/archivos/{subcarpeta}/{nombreArchivo}")
    public ResponseEntity<Resource> descargarArchivo(
            @PathVariable String subcarpeta, @PathVariable String nombreArchivo) {
        return fileStorageService.servirArchivo(subcarpeta, nombreArchivo, SUBCARPETAS_PERMITIDAS);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Modalidad parseModalidad(String valor) {
        try {
            return Modalidad.valueOf(valor.trim().toUpperCase());
        } catch (Exception e) {
            throw new ValidacionException("Modalidad inválida. Valores permitidos: PRESENCIAL, VIRTUAL, HIBRIDO.");
        }
    }

    private EstadoCurso parseEstado(String valor) {
        try {
            return EstadoCurso.valueOf(valor.trim().toUpperCase().replace(' ', '_'));
        } catch (Exception e) {
            throw new ValidacionException("Estado inválido. Valores permitidos: EN_ESPERA, EN_PROCESO, FINALIZADO.");
        }
    }

    private EstadoCurso parseEstadoOpcional(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return parseEstado(valor);
    }

    private Modalidad parseModalidadOpcional(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return parseModalidad(valor);
    }
}
