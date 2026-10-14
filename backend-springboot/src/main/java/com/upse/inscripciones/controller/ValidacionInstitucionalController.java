package com.upse.inscripciones.controller;

import com.upse.inscripciones.dto.ValidacionInstitucionalRequest;
import com.upse.inscripciones.dto.ValidacionInstitucionalResponse;
import com.upse.inscripciones.service.ConsultaInstitucionalRateLimiter;
import com.upse.inscripciones.service.ValidacionInstitucionalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/validacion-institucional")
@RequiredArgsConstructor
public class ValidacionInstitucionalController {
    private final ValidacionInstitucionalService service;
    private final ConsultaInstitucionalRateLimiter limite;

    @PostMapping
    public ResponseEntity<ValidacionInstitucionalResponse> consultar(
            @Valid @RequestBody ValidacionInstitucionalRequest body, HttpServletRequest request) {
        limite.comprobar(request.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.validar(body.identificacion(), body.tipoUsuario()));
    }
}
