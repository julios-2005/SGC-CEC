package com.upse.inscripciones.dto;
import java.math.BigDecimal;
public record ConsolidadoResponse(int anio, int participantes, BigDecimal ingresos,
        BigDecimal egresos, BigDecimal utilidad, BigDecimal gastosPersonal, BigDecimal utilidadCec,
        Long version) {}
