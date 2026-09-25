package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.DescuentoResponse;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.service.DescuentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/descuentos")
@RequiredArgsConstructor
/*
 * Nota de auditoría (decisión de negocio, no un olvido): este controller
 * NO restringe ninguno de sus endpoints a ADMIN_GENERAL, a propósito — ver
 * la misma nota en CursoController para el detalle completo. Cualquier
 * COORDINADOR con sesión activa puede crear/editar/eliminar cualquier
 * descuento del sistema.
 */
public class DescuentoController {

    private final DescuentoService descuentoService;

    // ── GET /api/descuentos ───────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<DescuentoResponse>> listarTodos() {
        return ResponseEntity.ok(descuentoService.listarTodos());
    }

    // ── GET /api/descuentos/activos ───────────────────────────────────────────
    @GetMapping("/activos")
    public ResponseEntity<List<DescuentoResponse>> listarActivos() {
        return ResponseEntity.ok(descuentoService.listarActivos());
    }

    // ── GET /api/descuentos/{id} ──────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<DescuentoResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(descuentoService.obtenerPorId(id));
    }

    // ── POST /api/descuentos ──────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<DescuentoResponse> registrar(
            @RequestParam("nombre")     String nombre,
            @RequestParam(value = "tipoUsuario", required = false) TipoUsuario tipoUsuario,
            @RequestParam("porcentaje") BigDecimal porcentaje) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(descuentoService.registrar(nombre, tipoUsuario, porcentaje));
    }

    // ── PUT /api/descuentos/{id} ──────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<DescuentoResponse> actualizar(
            @PathVariable Long id,
            @RequestParam("nombre")     String nombre,
            @RequestParam(value = "tipoUsuario", required = false) TipoUsuario tipoUsuario,
            @RequestParam("porcentaje") BigDecimal porcentaje) {

        return ResponseEntity.ok(descuentoService.actualizar(id, nombre, tipoUsuario, porcentaje));
    }

    // ── PATCH /api/descuentos/{id}/estado ────────────────────────────────────
    @PatchMapping("/{id}/estado")
    public ResponseEntity<DescuentoResponse> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean estado) {
        return ResponseEntity.ok(descuentoService.cambiarEstado(id, estado));
    }

    // ── DELETE /api/descuentos/{id} ───────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        descuentoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
