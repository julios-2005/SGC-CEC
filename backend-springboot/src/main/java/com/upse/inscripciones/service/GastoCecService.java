package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.*;
import com.upse.inscripciones.entity.GastoCec;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.GastoCecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class GastoCecService {
    private final GastoCecRepository repository;
    private final InformeEconomicoService informes;

    @Transactional(readOnly = true)
    public ConsolidadoResponse obtener(int anio) {
        validarAnio(anio);
        var gasto = repository.findById(anio).orElse(null);
        BigDecimal importe = gasto != null ? gasto.getImporte() : BigDecimal.ZERO;
        var datos = informes.listarResumen().stream()
                .filter(r -> (r.getAnio()==null?r.getFechaInicio().getYear():r.getAnio())==anio && !(anio==2026 && r.isExcluido2026())).toList();
        int participantes = datos.stream()
                .mapToInt(d -> d.getParticipantes() == null ? 0 : d.getParticipantes())
                .sum();
        BigDecimal ingresos = datos.stream().map(InformeEconomicoResumenResponse::getIngresos).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal egresos = datos.stream().map(InformeEconomicoResumenResponse::getEgresos).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal utilidad = ingresos.subtract(egresos);
        return new ConsolidadoResponse(anio, participantes, ingresos, egresos, utilidad,
                importe, utilidad.subtract(importe), gasto == null ? -1L : gasto.getVersion());
    }

    @Transactional
    public ConsolidadoResponse actualizar(int anio, GastoCecRequest r) {
        validarAnio(anio);
        var gasto = repository.findById(anio).orElse(null);
        Long versionActual = gasto == null ? -1L : gasto.getVersion();
        if (!versionActual.equals(r.version()))
            throw new ValidacionException("Los gastos fueron modificados por otra persona. Actualiza la pantalla antes de guardar.");
        if (gasto == null) { gasto = new GastoCec(); gasto.setAnio(anio); }
        gasto.setImporte(r.importe());
        repository.saveAndFlush(gasto);
        return obtener(anio);
    }

    private void validarAnio(int anio) {
        if (anio < 2000 || anio > 2100) throw new ValidacionException("El año debe estar entre 2000 y 2100.");
    }
}