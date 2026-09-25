package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.InscripcionResponse;
import com.upse.inscripciones.entity.EstadoInscripcion;
import com.upse.inscripciones.entity.Sexo;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.service.FileStorageService;
import com.upse.inscripciones.service.InscripcionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/inscripciones")
@RequiredArgsConstructor
public class InscripcionController {

    private final InscripcionService inscripcionService;
    private final FileStorageService fileStorageService;
    private final com.upse.inscripciones.service.ExcelTablaExportService excelTablaExportService;

    /**
     * Registra una nueva inscripción. Recibe multipart/form-data porque
     * incluye archivos (comprobante de pago, copia de cédula y, según el
     * tipo de usuario, comprobante de matrícula o carnet de discapacidad).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InscripcionResponse> registrar(
            @RequestParam("tipoUsuario") TipoUsuario tipoUsuario,
            @RequestParam("nombreCompleto") String nombreCompleto,
            @RequestParam("idPlanificacion") Long idPlanificacion,   // ✅ CAMBIO: antes era String curso
            @RequestParam("cedula") String cedula,
            @RequestParam("telefono") String telefono,
            @RequestParam("correoElectronico") String correoElectronico,
            @RequestParam("direccion") String direccion,
            @RequestParam("sexo") Sexo sexo,
            @RequestParam("comprobantePago") MultipartFile comprobantePago,
            @RequestParam("copiaCedula") MultipartFile copiaCedula,
            @RequestParam(value = "documentoAdicional", required = false) MultipartFile documentoAdicional) {

        InscripcionResponse response = inscripcionService.registrarInscripcion(
                tipoUsuario, nombreCompleto, idPlanificacion, cedula, telefono, correoElectronico, direccion, sexo,
                comprobantePago, copiaCedula, documentoAdicional);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Listado paginado. Parámetros opcionales:
     * - texto: filtra por nombre, cédula o nombre de curso.
     * - estado: filtra por estado exacto (PENDIENTE, ACEPTADA, etc.).
     * - tipoUsuario: filtra por tipo de participante.
     * - idPlanificacion: filtra por una planificación (curso) puntual.
     * - fechaDesde/fechaHasta: rango de fecha de registro (ISO yyyy-MM-dd,
     *   igual que un <input type="date">). fechaHasta incluye todo ese día.
     * - page/size/sort: paginación estándar de Spring (?page=0&size=20&sort=fechaRegistro,desc).
     * Por defecto: 20 por página, ordenado por fecha de registro descendente.
     */
    @GetMapping
    public ResponseEntity<Page<InscripcionResponse>> listarTodas(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoInscripcion estado,
            @RequestParam(required = false) TipoUsuario tipoUsuario,
            @RequestParam(required = false) Long idPlanificacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @PageableDefault(size = 20, sort = "fechaRegistro", direction = Sort.Direction.DESC) Pageable pageable) {
        LocalDateTime desde = fechaDesde != null ? LocalDateTime.of(fechaDesde, LocalTime.MIN) : null;
        LocalDateTime hasta = fechaHasta != null ? LocalDateTime.of(fechaHasta, LocalTime.MAX) : null;
        return ResponseEntity.ok(inscripcionService.listarTodas(
                texto, estado, tipoUsuario, idPlanificacion, desde, hasta, pageable));
    }

    // ── GET /api/inscripciones/exportar ───────────────────────────────────────
    // El rol COBROS solo puede consultar/descargar inscripciones, no exportarlas.
    @PreAuthorize("!hasRole('COBROS')")
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) EstadoInscripcion estado,
            @RequestParam(required = false) TipoUsuario tipoUsuario,
            @RequestParam(required = false) Long idPlanificacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) throws java.io.IOException {

        LocalDateTime desde = fechaDesde != null ? LocalDateTime.of(fechaDesde, LocalTime.MIN) : null;
        LocalDateTime hasta = fechaHasta != null ? LocalDateTime.of(fechaHasta, LocalTime.MAX) : null;

        List<InscripcionResponse> inscripciones = inscripcionService.listarParaExportar(
                texto, estado, tipoUsuario, idPlanificacion, desde, hasta);

        if (inscripciones.isEmpty()) {
            throw new com.upse.inscripciones.exception.ValidacionException(
                    "No hay inscripciones que coincidan con los filtros para exportar.");
        }

        List<com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel<InscripcionResponse>> columnas = List.of(
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Nombre completo", InscripcionResponse::getNombreCompleto),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Cédula", InscripcionResponse::getCedula),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Tipo de usuario", i -> i.getTipoUsuario() != null ? i.getTipoUsuario().getDescripcion() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Curso", i -> i.getPlanificacion() != null ? i.getPlanificacion().getNombreCurso() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Teléfono", InscripcionResponse::getTelefono),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Correo", InscripcionResponse::getCorreoElectronico),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Dirección", InscripcionResponse::getDireccion),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Sexo", i -> i.getSexo() != null ? i.getSexo().name() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Estado", i -> i.getEstado() != null ? i.getEstado().name() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Fecha de registro", InscripcionResponse::getFechaRegistro)
        );

        byte[] excel = excelTablaExportService.generar("Inscripciones", columnas, inscripciones);
        String nombreArchivo = "inscripciones-" + java.time.LocalDate.now() + ".xlsx";
        return com.upse.inscripciones.util.DescargaExcelUtil.responder(excel, nombreArchivo);
    }

    // ✅ NUEVO: inscritos de una planificación específica
    // El rol COBROS no puede filtrar por planificación (ver el @PreAuthorize de este método).
    @PreAuthorize("!hasRole('COBROS')")
    @GetMapping("/planificacion/{idPlanificacion}")
    public ResponseEntity<List<InscripcionResponse>> listarPorPlanificacion(@PathVariable Long idPlanificacion) {
        return ResponseEntity.ok(inscripcionService.listarPorPlanificacion(idPlanificacion));
    }

    // ✅ NUEVO: la secretaria acepta o rechaza una inscripción
    // El rol COBROS solo puede leer inscripciones, nunca aceptar/rechazar/anular.
    @PreAuthorize("!hasRole('COBROS')")
    @PatchMapping("/{id}/estado")
    public ResponseEntity<InscripcionResponse> cambiarEstado(
            @PathVariable Long id,
            @RequestParam("estado") EstadoInscripcion estado) {
        return ResponseEntity.ok(inscripcionService.cambiarEstado(id, estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InscripcionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(inscripcionService.obtenerPorId(id));
    }

    // Requiere sesión (ver JwtAuthenticationFilter). Se restringe a las
    // subcarpetas que efectivamente contienen documentos de inscripción.
    private static final List<String> SUBCARPETAS_PERMITIDAS = List.of(
            "comprobantes-pago", "copias-cedula", "documentos-adicionales",
            "comprobantes-matricula-generados");

    /**
     * Sirve un archivo previamente subido a partir de su ruta relativa,
     * ej: /api/inscripciones/archivos/comprobantes-pago/uuid.pdf
     */
    @GetMapping("/archivos/{subcarpeta}/{nombreArchivo}")
    public ResponseEntity<Resource> descargarArchivo(
            @PathVariable String subcarpeta,
            @PathVariable String nombreArchivo) {
        return fileStorageService.servirArchivo(subcarpeta, nombreArchivo, SUBCARPETAS_PERMITIDAS);
    }
}
