package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Modalidad;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeEconomicoResumenResponse {
    private Long idPlanificacion;
    private Long idInforme;
    private Integer anio;
    private boolean cifrasPendientes;
    private boolean excluido2026;
    private String observacion;
    private String nombreCurso;
    private Modalidad modalidad;        // ✅ NUEVO: para filtrar y mostrar en informe-economico.html
    private LocalDate fechaInicio;      // ✅ NUEVO: para filtrar por rango de fechas
    private Integer participantes;
    private BigDecimal ingresos;
    private BigDecimal egresos;
    private BigDecimal utilidad;
}
