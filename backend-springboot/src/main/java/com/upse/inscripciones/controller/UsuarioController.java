package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.RestablecerContrasenaResponse;
import com.upse.inscripciones.dto.UsuarioRequest;
import com.upse.inscripciones.dto.UsuarioResponse;
import com.upse.inscripciones.entity.Rol;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.service.UsuarioService;
import com.upse.inscripciones.util.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /**
     * Obtiene todos los usuarios, paginado (solo ADMIN_GENERAL).
     * Por defecto: 20 por página, ordenado por nombreUsuario.
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @GetMapping
    public ResponseEntity<Page<UsuarioResponse>> obtenerTodos(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) Boolean estado,
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(usuarioService.obtenerTodos(texto, parseRolOpcional(rol), estado, pageable));
    }

    private Rol parseRolOpcional(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Rol.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Rol inválido: " + valor);
        }
    }

    /**
     * Obtiene un usuario por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerPorId(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long usuarioLogueado = RequestAuthUtils.obtenerUsuarioId(request);
        String rol = RequestAuthUtils.obtenerRol(request);

        // Solo puede ver su propio usuario o si es ADMIN_GENERAL
        if (!id.equals(usuarioLogueado) && !"ADMIN_GENERAL".equals(rol)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    /**
     * Crea un nuevo usuario (solo ADMIN_GENERAL)
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PostMapping
    public ResponseEntity<UsuarioResponse> crearUsuario(
            @Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse response = usuarioService.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Actualiza un usuario (solo ADMIN_GENERAL)
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse response = usuarioService.actualizarUsuario(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Desactiva un usuario (solo ADMIN_GENERAL)
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @DeleteMapping("/{id}")
    public ResponseEntity<UsuarioResponse> desactivarUsuario(
            @PathVariable Long id) {
        UsuarioResponse response = usuarioService.desactivarUsuario(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Activa un usuario (solo ADMIN_GENERAL)
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PutMapping("/{id}/activar")
    public ResponseEntity<UsuarioResponse> activarUsuario(
            @PathVariable Long id) {
        UsuarioResponse response = usuarioService.activarUsuario(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Restablece la contraseña de un usuario (solo ADMIN_GENERAL)
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @PostMapping("/{id}/restablecer-contraseña")
    public ResponseEntity<RestablecerContrasenaResponse> restablecerContraseña(
            @PathVariable Long id) {
        RestablecerContrasenaResponse response = usuarioService.restablecerContraseña(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina permanentemente un usuario de la base de datos (solo ADMIN_GENERAL).
     * A diferencia de desactivarUsuario (borrado lógico, conserva el registro),
     * esto borra la fila por completo y no se puede deshacer.
     */
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @DeleteMapping("/{id}/eliminar")
    public ResponseEntity<?> eliminarUsuario(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long usuarioLogueado = RequestAuthUtils.obtenerUsuarioId(httpRequest);
        if (id.equals(usuarioLogueado)) {
            return ResponseEntity.badRequest().body(new Object() {
                public final String mensaje = "No puedes eliminar tu propio usuario mientras tienes la sesión iniciada.";
                public final Boolean exito = false;
            });
        }

        usuarioService.eliminarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
