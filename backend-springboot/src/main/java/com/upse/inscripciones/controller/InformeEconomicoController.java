package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.InformeEconomicoDetalleResponse;
import com.upse.inscripciones.dto.InformeEconomicoResumenResponse;
import com.upse.inscripciones.entity.InformeEconomico;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.service.InformeEconomicoExcelService;
import com.upse.inscripciones.service.InformeEconomicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Consulta y exportación de informes; edición explícita de egresos manuales y gastos anuales del CEC. */
@RestController
@RequestMapping("/api/informes-economicos")
@RequiredArgsConstructor
/*
 * Nota de auditoría (decisión de negocio, no un olvido): este controller
 * NO restringe ninguno de sus endpoints a ADMIN_GENERAL, a propósito — ver
 * la misma nota en CursoController para el detalle completo. Cualquier
 * COORDINADOR con sesión activa puede exportar el informe económico
 * completo, con las cifras de ingresos de TODOS los cursos, no solo los
 * que coordina.
 */
public class InformeEconomicoController {

    private final InformeEconomicoService informeEconomicoService;
    private final InformeEconomicoExcelService informeEconomicoExcelService;
    private final com.upse.inscripciones.service.GastoCecService gastoCecService;

    @GetMapping("/referencias/{id}")
    public InformeEconomicoDetalleResponse referencia(@PathVariable Long id){validarReferencia(id);return informeEconomicoService.obtenerDetalle(-id);}
    @PostMapping("/referencias/{id}/egresos")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public InformeEconomicoDetalleResponse agregarReferencia(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.EgresoRequest request){
        validarReferencia(id);return informeEconomicoService.guardarEgreso(-id,null,request);
    }
    @PutMapping("/referencias/{id}/egresos/{linea}")
    public InformeEconomicoDetalleResponse editarReferencia(@PathVariable Long id,@PathVariable Long linea,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.EgresoRequest request){
        validarReferencia(id);return informeEconomicoService.guardarEgreso(-id,linea,request);
    }
    @DeleteMapping("/referencias/{id}/egresos/{linea}")
    public InformeEconomicoDetalleResponse quitarReferencia(@PathVariable Long id,@PathVariable Long linea){
        validarReferencia(id);return informeEconomicoService.eliminarEgreso(-id,linea);
    }
    private void validarReferencia(Long id){if(id==null || id<=0)throw new ValidacionException("Identificador de referencia inválido.");}
    @GetMapping("/consolidado/{anio}")
    public com.upse.inscripciones.dto.ConsolidadoResponse consolidado(@PathVariable int anio) {
        return gastoCecService.obtener(anio);
    }

    @PutMapping("/gastos-cec/{anio}")
    public com.upse.inscripciones.dto.ConsolidadoResponse guardarGastos(@PathVariable int anio,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.GastoCecRequest request) {
        return gastoCecService.actualizar(anio, request);
    }

    // ── Edición de la cantidad de participantes de una línea de ingreso ──────
    // Solo viaja la cantidad; el valor por participante lo conserva el
    // servidor según el tipo de participante de la línea.
    @PutMapping("/{id}/ingresos/{linea}")
    public InformeEconomicoDetalleResponse editarIngreso(@PathVariable Long id, @PathVariable Long linea,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.IngresoRequest request) {
        return informeEconomicoService.guardarIngreso(id, linea, request);
    }

    @DeleteMapping("/{id}/ingresos/{linea}")
    public InformeEconomicoDetalleResponse restablecerIngreso(@PathVariable Long id, @PathVariable Long linea) {
        return informeEconomicoService.restablecerIngreso(id, linea);
    }

    @PutMapping("/referencias/{id}/ingresos/{linea}")
    public InformeEconomicoDetalleResponse editarIngresoReferencia(@PathVariable Long id, @PathVariable Long linea,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.IngresoRequest request) {
        validarReferencia(id);
        return informeEconomicoService.guardarIngreso(-id, linea, request);
    }

    @DeleteMapping("/referencias/{id}/ingresos/{linea}")
    public InformeEconomicoDetalleResponse restablecerIngresoReferencia(@PathVariable Long id, @PathVariable Long linea) {
        validarReferencia(id);
        return informeEconomicoService.restablecerIngreso(-id, linea);
    }

    @PostMapping("/{id}/egresos")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public InformeEconomicoDetalleResponse agregarEgreso(@PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.EgresoRequest request) {
        return informeEconomicoService.guardarEgreso(id, null, request);
    }

    @PutMapping("/{id}/egresos/{linea}")
    public InformeEconomicoDetalleResponse editarEgreso(@PathVariable Long id, @PathVariable Long linea,
            @jakarta.validation.Valid @RequestBody com.upse.inscripciones.dto.EgresoRequest request) {
        return informeEconomicoService.guardarEgreso(id, linea, request);
    }

    @DeleteMapping("/{id}/egresos/{linea}")
    public InformeEconomicoDetalleResponse quitarEgreso(@PathVariable Long id, @PathVariable Long linea) {
        return informeEconomicoService.eliminarEgreso(id, linea);
    }

    // ── GET /api/informes-economicos ──────────────────────────────────────────
    // Parámetros opcionales: texto (nombre del curso), modalidad, y rango de
    // fecha de inicio (fechaDesde/fechaHasta, formato ISO yyyy-MM-dd).
    @GetMapping
    public ResponseEntity<List<InformeEconomicoResumenResponse>> listarResumen(
            @RequestParam(defaultValue="2026") int anio,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) {
        return ResponseEntity.ok(informeEconomicoService.listarResumen(
                texto, parseModalidadOpcional(modalidad), fechaDesde, fechaHasta).stream().filter(i->!(anio==2026 && i.isExcluido2026())).toList());
    }

    // ── GET /api/informes-economicos/exportar ────────────────────────────────
    // Descarga un .xlsx con el mismo formato que maneja el CEC a mano: una
    // hoja por curso/planificación (INGRESOS, EGRESOS, UTILIDAD) + una hoja
    // resumen final con fórmulas que referencian cada hoja de curso. Admite
    // los mismos filtros que el listado, para poder exportar justo lo que se
    // está viendo en pantalla (ej. solo un rango de fechas, o solo una
    // modalidad).
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(defaultValue = "2026") int anio,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta) throws IOException {

        List<InformeEconomico> informes = informeEconomicoService.listarParaExportar(
                texto, parseModalidadOpcional(modalidad), fechaDesde, fechaHasta);

        boolean anual = (texto == null || texto.isBlank()) && (modalidad == null || modalidad.isBlank())
                && fechaDesde == null && fechaHasta == null;
        var consolidado = gastoCecService.obtener(anio);
        informes=informes.stream().filter(i->!(anio==2026 && i.isExcluido2026())).toList();
        if(anual)informes=informes.stream().filter(i->i.getAnioReporte()==anio).toList();

        if (informes.isEmpty()) {
            throw new ValidacionException("No hay informes económicos que coincidan con los filtros para exportar.");
        }

        byte[] excel = informeEconomicoExcelService.generarExcel(informes, anual ? anio : null, consolidado.gastosPersonal());

        String marcaTiempo = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String nombreArchivo = "informe-economico-cec-" + marcaTiempo + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(nombreArchivo, StandardCharsets.UTF_8)
                        .build());
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        return ResponseEntity.ok().headers(headers).body(excel);
    }

    private Modalidad parseModalidadOpcional(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Modalidad.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Modalidad inválida. Valores permitidos: PRESENCIAL, VIRTUAL, HIBRIDO.");
        }
    }

    // ── GET /api/informes-economicos/{idPlanificacion} ───────────────────────
    @GetMapping("/{idPlanificacion}")
    public ResponseEntity<InformeEconomicoDetalleResponse> obtenerDetalle(@PathVariable Long idPlanificacion) {
        return ResponseEntity.ok(informeEconomicoService.obtenerDetalle(idPlanificacion));
    }
}
