package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.CambioContrasenaRequest;
import com.upse.inscripciones.dto.LoginRequest;
import com.upse.inscripciones.dto.LoginResponse;
import com.upse.inscripciones.service.AuthService;
import com.upse.inscripciones.service.CambioContrasenaService;
import com.upse.inscripciones.service.JwtService;
import com.upse.inscripciones.service.LoginRateLimiterService;
import com.upse.inscripciones.util.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CambioContrasenaService cambioContraseñaService;
    private final LoginRateLimiterService loginRateLimiterService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clave = claveIntentoLogin(httpRequest, request.getUsuario());
        long segundosRestantes = loginRateLimiterService.segundosDeBloqueoRestantes(clave);
        if (segundosRestantes > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosRestantes))
                    .body(LoginResponse.builder()
                            .exito(false)
                            .mensaje("Demasiados intentos fallidos. Intenta de nuevo en unos minutos.")
                            .build());
        }

        LoginResponse response = authService.validarCredenciales(request);
        if (Boolean.TRUE.equals(response.getExito())) {
            loginRateLimiterService.registrarIntentoExitoso(clave);
            response.setToken(jwtService.generarToken(response.getUsuario()));
            response.setTipoToken("Bearer");
            response.setExpiraEnSegundos(jwtService.getExpiraEnSegundos());
        } else {
            loginRateLimiterService.registrarIntentoFallido(clave);
        }
        return ResponseEntity.ok(response);
    }

    private String claveIntentoLogin(HttpServletRequest request, String usuario) {
        String usuarioNormalizado = usuario == null ? "" : usuario.trim().toLowerCase();
        return request.getRemoteAddr() + "|" + usuarioNormalizado;
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(new Object() {
            public final String mensaje = "Logout exitoso";
            public final Boolean exito = true;
        });
    }

    @PostMapping("/cambiar-contraseña")
    public ResponseEntity<?> cambiarContraseña(
            @Valid @RequestBody CambioContrasenaRequest request,
            HttpServletRequest httpRequest) {
        return cambiar(request, httpRequest, false);
    }

    @PostMapping("/cambiar-contraseña-obligatoria")
    public ResponseEntity<?> cambiarContraseñaObligatoria(
            @Valid @RequestBody CambioContrasenaRequest request,
            HttpServletRequest httpRequest) {
        return cambiar(request, httpRequest, true);
    }

    private ResponseEntity<?> cambiar(
            CambioContrasenaRequest request, HttpServletRequest httpRequest, boolean obligatorio) {
        Long usuarioId = RequestAuthUtils.obtenerUsuarioId(httpRequest);
        if (usuarioId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new Object() {
                public final String mensaje = "Usuario no autenticado";
                public final Boolean exito = false;
            });
        }
        if (obligatorio) cambioContraseñaService.cambiarContraseñaObligatoria(usuarioId, request);
        else cambioContraseñaService.cambiarContraseña(usuarioId, request);
        return ResponseEntity.ok(new Object() {
            public final String mensaje = "Contraseña cambiada exitosamente";
            public final Boolean exito = true;
        });
    }

    @GetMapping("/sesion-actual")
    public ResponseEntity<?> sesionActual(HttpServletRequest request) {
        return ResponseEntity.ok(new Object() {
            public final Boolean exito = true;
            public final String nombreCompleto = RequestAuthUtils.obtenerNombre(request);
            public final String foto = RequestAuthUtils.obtenerFoto(request);
            public final String rol = RequestAuthUtils.obtenerRol(request);
            public final Long usuarioId = RequestAuthUtils.obtenerUsuarioId(request);
        });
    }
}
