package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.EspecialistaRequest;
import com.upse.inscripciones.dto.EspecialistaResponse;
import com.upse.inscripciones.service.EspecialistaListadoPdfService;
import com.upse.inscripciones.service.EspecialistaService;
import com.upse.inscripciones.util.Nacionalidades;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/especialistas")
@RequiredArgsConstructor
/*
 * Nota de auditoría (decisión de negocio, no un olvido): este controller
 * NO restringe ninguno de sus endpoints a ADMIN_GENERAL, a propósito — ver
 * la misma nota en CursoController para el detalle completo. Cualquier
 * COORDINADOR con sesión activa puede crear/editar/eliminar cualquier
 * especialista del sistema.
 */
public class EspecialistaController {

    private final EspecialistaService especialistaService;
    private final EspecialistaListadoPdfService especialistaListadoPdfService;
    private final com.upse.inscripciones.service.ExcelTablaExportService excelTablaExportService;

    // ── GET /api/especialistas/listado-pdf ─────────────────────────────────────────
    // Genera un PDF descargable con todos los especialistas y los cursos que han dictado.
    @GetMapping("/listado-pdf")
    public ResponseEntity<byte[]> listadoPdf() {
        byte[] pdf = especialistaListadoPdfService.generarListado();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"listado-especialistas.pdf\"")
                .body(pdf);
    }

    // ── GET /api/especialistas/{id}/listado-pdf ─────────────────────────────────────
    // Usado por consulta-especialistas.html: PDF con solo los cursos de UN especialista.
    @GetMapping("/{id}/listado-pdf")
    public ResponseEntity<byte[]> listadoPdfPorEspecialista(@PathVariable Long id) {
        byte[] pdf = especialistaListadoPdfService.generarListadoPorEspecialista(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"listado-cursos-especialista.pdf\"")
                .body(pdf);
    }

    // ── GET /api/especialistas/nacionalidades ───────────────────────────────────────
    // Catálogo código ISO -> {nombre del país, gentilicio neutro}, para llenar
    // el <select> de Nacionalidad en especialistas.html. Un solo lugar
    // (Nacionalidades.java) alimenta tanto esto como el cálculo del
    // gentilicio en EspecialistaResponse, para que nunca queden desincronizados.
    @GetMapping("/nacionalidades")
    public ResponseEntity<Map<String, String[]>> nacionalidades() {
        return ResponseEntity.ok(Nacionalidades.todas());
    }

    // ── GET /api/especialistas ─────────────────────────────────────────────────────
    // Listado paginado para especialistas.html, con búsqueda opcional por nombre,
    // apellido, cédula o especialidad (?texto=...). Por defecto: 20 por
    // página, ordenado por nombres.
    @GetMapping
    public ResponseEntity<Page<EspecialistaResponse>> listarTodos(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean estado,
            @RequestParam(required = false) String nacionalidad,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(especialistaService.listarPaginado(texto, estado, nacionalidad, pageable));
    }

    // ── GET /api/especialistas/activos ─────────────────────────────────────────────
    // Se mantiene la ruta (la usa el buscador de especialistas), pero ya no
    // filtra por estado: "Activo"/"Disponible" ahora es automático (ver
    // EspecialistaService#recalcularEstado) y este buscador necesita poder
    // encontrar también a quienes no están dictando clases en este momento.
    @GetMapping("/activos")
    public ResponseEntity<List<EspecialistaResponse>> listarActivos() {
        return ResponseEntity.ok(especialistaService.listarTodos());
    }

    // ── GET /api/especialistas/exportar ─────────────────────────────────────────────
    // Descarga un .xlsx con el listado de especialistas (mismos filtros que /api/especialistas).
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean estado,
            @RequestParam(required = false) String nacionalidad) throws java.io.IOException {

        List<EspecialistaResponse> especialistas = especialistaService.listarParaExportar(texto, estado, nacionalidad);

        if (especialistas.isEmpty()) {
            throw new com.upse.inscripciones.exception.ValidacionException(
                    "No hay especialistas que coincidan con los filtros para exportar.");
        }

        List<com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel<EspecialistaResponse>> columnas = List.of(
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Cédula", EspecialistaResponse::getCedula),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Nombres", EspecialistaResponse::getNombres),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Apellidos", EspecialistaResponse::getApellidos),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Teléfono", EspecialistaResponse::getTelefono),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Correo", EspecialistaResponse::getCorreo),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Especialidad", EspecialistaResponse::getEspecialidad),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Área de conocimiento", EspecialistaResponse::getAreaConocimiento),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Nacionalidad", EspecialistaResponse::getNombrePaisNacionalidad),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Estado", e -> Boolean.TRUE.equals(e.getEstado()) ? "Activo" : "Disponible")
        );

        byte[] excel = excelTablaExportService.generar("Especialistas", columnas, especialistas);
        String nombreArchivo = "especialistas-" + java.time.LocalDate.now() + ".xlsx";
        return com.upse.inscripciones.util.DescargaExcelUtil.responder(excel, nombreArchivo);
    }

    // ── GET /api/especialistas/{id} ────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<EspecialistaResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(especialistaService.obtenerPorId(id));
    }

    // ── POST /api/especialistas ────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<EspecialistaResponse> registrar(@Valid EspecialistaRequest request) {
        EspecialistaResponse response = especialistaService.registrar(
                request.getCedula(), request.getNombres(), request.getApellidos(),
                request.getTelefono(), request.getCorreo(),
                request.getEspecialidad(), request.getAreaConocimiento(),
                request.getPaisNacionalidad());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── PUT /api/especialistas/{id} ────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<EspecialistaResponse> actualizar(@PathVariable Long id, @Valid EspecialistaRequest request) {
        return ResponseEntity.ok(especialistaService.actualizar(
                id, request.getCedula(), request.getNombres(), request.getApellidos(),
                request.getTelefono(), request.getCorreo(),
                request.getEspecialidad(), request.getAreaConocimiento(),
                request.getPaisNacionalidad()));
    }

    // ── PATCH /api/especialistas/{id}/estado ──────────────────────────────────────
    @PatchMapping("/{id}/estado")
    public ResponseEntity<EspecialistaResponse> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean estado) {
        return ResponseEntity.ok(especialistaService.cambiarEstado(id, estado));
    }

    // ── DELETE /api/especialistas/{id} ─────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        especialistaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
