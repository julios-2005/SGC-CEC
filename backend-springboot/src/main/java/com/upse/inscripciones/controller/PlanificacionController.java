package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.CoordinadorResponse;
import com.upse.inscripciones.dto.CursoResponse;
import com.upse.inscripciones.dto.EspecialistaResponse;
import com.upse.inscripciones.dto.PlanificacionResponse;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.service.CoordinadorService;
import com.upse.inscripciones.service.CursoService;
import com.upse.inscripciones.service.EspecialistaService;
import com.upse.inscripciones.service.PlanificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/planificaciones")
@RequiredArgsConstructor
/*
 * Nota de auditoría (decisión de negocio, no un olvido): este controller
 * NO restringe ninguno de sus endpoints a ADMIN_GENERAL, a propósito — ver
 * la misma nota en CursoController para el detalle completo. Cualquier
 * COORDINADOR con sesión activa puede crear/editar/eliminar cualquier
 * planificación del sistema, no solo las suyas.
 */
public class PlanificacionController {

    private final PlanificacionService planificacionService;
    private final CoordinadorService coordinadorService;  // ✅ NUEVO
    private final EspecialistaService especialistaService;          // ✅ NUEVO
    private final CursoService cursoService;               // ✅ NUEVO
    private final com.upse.inscripciones.service.ExcelTablaExportService excelTablaExportService;

    // ✅ NUEVO ENDPOINT: Obtener cursos (para combobox de "actividad")
    @GetMapping("/cursos")
    public ResponseEntity<List<CursoResponse>> listarCursos() {
        return ResponseEntity.ok(cursoService.listarTodos());
    }

    // ✅ NUEVO ENDPOINT: Obtener coordinadores activos (para combobox)
    @GetMapping("/coordinadores")
    public ResponseEntity<List<CoordinadorResponse>> listarCoordinadores() {
        return ResponseEntity.ok(coordinadorService.listarActivos());
    }

    // Combobox de especialistas para armar una planificación: se listan
    // TODOS, sin filtrar por estado. "Activo"/"Disponible" ahora refleja si
    // el especialista está dictando clases ahora mismo (ver
    // EspecialistaService#recalcularEstado) — filtrar por eso aquí
    // dejaría fuera justo a quienes SÍ están disponibles para una
    // planificación nueva.
    @GetMapping("/especialistas")
    public ResponseEntity<List<EspecialistaResponse>> listarEspecialistas() {
        return ResponseEntity.ok(especialistaService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<PlanificacionResponse> registrar(
            @RequestParam("idCurso") Long idCurso,              // ✅ CAMBIO: ID en lugar de String
            @RequestParam("idCoordinador") Long idCoordinador,  // ✅ CAMBIO: ID en lugar de String
            @RequestParam("idEspecialista") Long idEspecialista,          // ✅ CAMBIO: ID en lugar de String
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("horario") String horario,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam("modalidad") Modalidad modalidad,
            @RequestParam(value = "costoEspecialista", required = false) java.math.BigDecimal costoEspecialista,
            @RequestParam(value = "docenteIds", required = false) List<Long> docenteIds,
            @RequestParam(value="honorariosDocentes",required=false) List<java.math.BigDecimal> honorariosDocentes) {

        PlanificacionResponse response = planificacionService.guardarConDocentes(
                null, idCurso, idCoordinador, idEspecialista, fechaInicio, horario, fechaFin, modalidad, costoEspecialista, docenteIds, honorariosDocentes);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanificacionResponse> actualizar(
            @PathVariable Long id,
            @RequestParam("idCurso") Long idCurso,
            @RequestParam("idCoordinador") Long idCoordinador,
            @RequestParam("idEspecialista") Long idEspecialista,
            @RequestParam("fechaInicio") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam("horario") String horario,
            @RequestParam("fechaFin") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam("modalidad") Modalidad modalidad,
            @RequestParam(value = "costoEspecialista", required = false) java.math.BigDecimal costoEspecialista,
            @RequestParam(value = "docenteIds", required = false) List<Long> docenteIds,
            @RequestParam(value="honorariosDocentes",required=false) List<java.math.BigDecimal> honorariosDocentes) {

        PlanificacionResponse response = planificacionService.guardarConDocentes(
                id, idCurso, idCoordinador, idEspecialista, fechaInicio, horario, fechaFin, modalidad, costoEspecialista, docenteIds, honorariosDocentes);

        return ResponseEntity.ok(response);
    }

    // Listado paginado para la pantalla de gestión (Planificaciones.html).
    // Parámetros opcionales: texto (curso, coordinador o especialista), modalidad,
    // rango de fecha de inicio (fechaDesde/fechaHasta, ISO yyyy-MM-dd) y
    // estadoPlanificacion ("ACTIVA" = no ha finalizado, "FINALIZADA" = ya
    // terminó; cualquier otro valor u omitirlo trae ambas).
    // Por defecto: 20 por página, ordenado por fecha de inicio ascendente
    // (mismo orden que antes se hacía en el navegador).
    @GetMapping
    public ResponseEntity<Page<PlanificacionResponse>> listarTodas(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Modalidad modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estadoPlanificacion,
            @PageableDefault(size = 20, sort = "fechaRegistro", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(planificacionService.listarTodas(texto, modalidad, fechaDesde, fechaHasta, estadoPlanificacion, pageable));
    }

    // ── GET /api/planificaciones/exportar ─────────────────────────────────────
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Modalidad modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) String estadoPlanificacion) throws java.io.IOException {

        List<PlanificacionResponse> planificaciones =
                planificacionService.listarParaExportar(texto, modalidad, fechaDesde, fechaHasta, estadoPlanificacion);

        if (planificaciones.isEmpty()) {
            throw new com.upse.inscripciones.exception.ValidacionException(
                    "No hay planificaciones que coincidan con los filtros para exportar.");
        }

        List<com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel<PlanificacionResponse>> columnas = List.of(
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Curso", p -> p.getCurso() != null ? p.getCurso().getNombre() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Código curso", p -> p.getCurso() != null ? p.getCurso().getCodigo() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Coordinador(a)", p -> p.getCoordinador() != null
                        ? p.getCoordinador().getNombres() + " " + p.getCoordinador().getApellidos() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Docentes", PlanificacionResponse::getNombresDocentes),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Fecha inicio", PlanificacionResponse::getFechaInicio),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Fecha fin", PlanificacionResponse::getFechaFin),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Horario", PlanificacionResponse::getHorario),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Modalidad", p -> p.getModalidad() != null ? p.getModalidad().name() : null),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.de("Estado", p -> p.isActiva() ? "Activa" : "Finalizada"),
                com.upse.inscripciones.service.ExcelTablaExportService.ColumnaExcel.moneda("Costo especialista", PlanificacionResponse::getCostoEspecialista)
        );

        byte[] excel = excelTablaExportService.generar("Planificaciones", columnas, planificaciones);
        String nombreArchivo = "planificaciones-" + java.time.LocalDate.now() + ".xlsx";
        return com.upse.inscripciones.util.DescargaExcelUtil.responder(excel, nombreArchivo);
    }

    // ── GET /api/planificaciones/vigentes ─────────────────────────────────────
    // Público: solo cursos que aún no terminaron y que todavía tienen cupo.
    // Lo usa cursos-disponibles.html (la página a la que apunta el QR).
    @GetMapping("/vigentes")
    public ResponseEntity<List<PlanificacionResponse>> listarVigentes() {
        return ResponseEntity.ok(planificacionService.listarVigentes());
    }

    // ── GET /api/planificaciones/especialista/{idEspecialista} ───────────────────────────
    // Usado por consulta-especialistas.html: cursos dictados por un especialista puntual.
    @GetMapping("/especialista/{idEspecialista}")
    public ResponseEntity<List<PlanificacionResponse>> listarPorEspecialista(@PathVariable Long idEspecialista) {
        return ResponseEntity.ok(planificacionService.listarPorEspecialista(idEspecialista));
    }

    // ── GET /api/planificaciones/coordinador/{idCoordinador} ───────────────────
    // Usado por consulta-coordinadores.html: cursos a cargo de un coordinador puntual.
    @GetMapping("/coordinador/{idCoordinador}")
    public ResponseEntity<List<PlanificacionResponse>> listarPorCoordinador(@PathVariable Long idCoordinador) {
        return ResponseEntity.ok(planificacionService.listarPorCoordinador(idCoordinador));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanificacionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(planificacionService.obtenerPorId(id));
    }

    // costo-especialista
    @PatchMapping("/{id}/costo-especialista")
    public ResponseEntity<PlanificacionResponse> actualizarCostoEspecialista(
            @PathVariable Long id,
            @RequestParam("costoEspecialista") java.math.BigDecimal costoEspecialista) {
        return ResponseEntity.ok(planificacionService.actualizarCostoEspecialista(id, costoEspecialista));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        planificacionService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
