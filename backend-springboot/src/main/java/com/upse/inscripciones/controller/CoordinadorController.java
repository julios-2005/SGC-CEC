package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.CoordinadorRequest;
import com.upse.inscripciones.dto.CoordinadorResponse;
import com.upse.inscripciones.service.CoordinadorListadoPdfService;
import com.upse.inscripciones.service.CoordinadorService;
import com.upse.inscripciones.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/coordinadores")
@RequiredArgsConstructor
public class CoordinadorController {

    private final CoordinadorService coordinadorService;
    private final FileStorageService fileStorageService;
    private final CoordinadorListadoPdfService coordinadorListadoPdfService;
    private final com.upse.inscripciones.service.ExcelTablaExportService excelTablaExportService;

    // ── GET /api/coordinadores ────────────────────────────────────────────────
    // Listado paginado para coordinadores.html, con búsqueda opcional por
    // nombre, apellido, cédula o correo (?texto=...). Por defecto: 20 por
    // página, ordenado por nombres.
    @GetMapping
    public ResponseEntity<Page<CoordinadorResponse>> listarTodos(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean estado,
            @PageableDefault(size = 20, sort = "id", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(coordinadorService.listarPaginado(texto, estado, pageable));
    }

    // ── GET /api/coordinadores/exportar ───────────────────────────────────────
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean estado) throws java.io.IOException {

        List<CoordinadorResponse> coordinadores = coordinadorService.listarParaExportar(texto, estado);

        if (coordinadores.isEmpty()) {
            throw new com.upse.inscripciones.exception.ValidacionException(
                    "No hay coordinadores que coincidan con los filtros para exportar.");
        }

        List<com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel<CoordinadorResponse>> columnas = List.of(
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Cédula", CoordinadorResponse::getCedula),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Nombres", CoordinadorResponse::getNombres),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Apellidos", CoordinadorResponse::getApellidos),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Teléfono", CoordinadorResponse::getTelefono),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Correo", CoordinadorResponse::getCorreo),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Estado", c -> Boolean.TRUE.equals(c.getEstado()) ? "Activo" : "Inactivo")
        );

        byte[] excel = excelTablaExportService.generar("Coordinadores", columnas, coordinadores);
        String nombreArchivo = "coordinadores-" + java.time.LocalDate.now() + ".xlsx";
        return com.upse.inscripciones.util.DescargaExcelUtil.responder(excel, nombreArchivo);
    }

    // ── GET /api/coordinadores/activos ────────────────────────────────────────
    @GetMapping("/activos")
    public ResponseEntity<List<CoordinadorResponse>> listarActivos() {
        return ResponseEntity.ok(coordinadorService.listarActivos());
    }

    // ── GET /api/coordinadores/{id} ───────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<CoordinadorResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(coordinadorService.obtenerPorId(id));
    }

    // ── GET /api/coordinadores/{id}/listado-pdf ───────────────────────────────
    // Usado por consulta-coordinadores.html: PDF con solo los cursos de UN coordinador.
    @GetMapping("/{id}/listado-pdf")
    public ResponseEntity<byte[]> listadoPdfPorCoordinador(@PathVariable Long id) {
        byte[] pdf = coordinadorListadoPdfService.generarListadoPorCoordinador(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"listado-cursos-coordinador.pdf\"")
                .body(pdf);
    }

    // ── POST /api/coordinadores ───────────────────────────────────────────────
    // multipart/form-data porque opcionalmente incluye la foto
    // Solo ADMIN_GENERAL puede dar de alta coordinadores.
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CoordinadorResponse> registrar(@Valid CoordinadorRequest request) {
        CoordinadorResponse response = coordinadorService.registrar(
                request.getCedula(), request.getNombres(), request.getApellidos(),
                request.getTelefono(), request.getCorreo(), request.getFoto());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── PUT /api/coordinadores/{id} ───────────────────────────────────────────
    // Solo ADMIN_GENERAL puede editar coordinadores.
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, path = "/{id}")
    public ResponseEntity<CoordinadorResponse> actualizar(
            @PathVariable Long id, @Valid CoordinadorRequest request) {
        return ResponseEntity.ok(coordinadorService.actualizar(
                id, request.getCedula(), request.getNombres(), request.getApellidos(),
                request.getTelefono(), request.getCorreo(), request.getFoto()));
    }

    // ── PATCH /api/coordinadores/{id}/estado ──────────────────────────────────
    // Solo ADMIN_GENERAL puede activar/desactivar coordinadores.
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<CoordinadorResponse> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean estado) {
        return ResponseEntity.ok(coordinadorService.cambiarEstado(id, estado));
    }

    // ── DELETE /api/coordinadores/{id} ────────────────────────────────────────
    // Solo ADMIN_GENERAL puede eliminar coordinadores.
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        coordinadorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // Requiere sesión (ver JwtAuthenticationFilter), pero igual se restringe
    // a la única subcarpeta que le corresponde a este controller.
    private static final List<String> SUBCARPETAS_PERMITIDAS = List.of("fotos-coordinadores");

    // ── GET /api/coordinadores/archivos/fotos-coordinadores/{archivo} ────────
    @GetMapping("/archivos/{subcarpeta}/{nombreArchivo}")
    public ResponseEntity<Resource> descargarArchivo(
            @PathVariable String subcarpeta, @PathVariable String nombreArchivo) {
        return fileStorageService.servirArchivo(subcarpeta, nombreArchivo, SUBCARPETAS_PERMITIDAS);
    }
}
