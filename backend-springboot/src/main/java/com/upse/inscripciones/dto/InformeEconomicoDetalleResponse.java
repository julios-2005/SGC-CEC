package com.upse.inscripciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InformeEconomicoDetalleResponse {
    private Long idPlanificacion;
    private Long idInforme;
    private Integer anio;
    private boolean cifrasPendientes;
    private boolean excluido2026;
    private String observacion;
    private String nombreCurso;
    private String modalidad;
    private String especialista;
    private String coordinador;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer participantes;

    private List<IngresoLineaResponse> ingresos;
    private BigDecimal ingresosTotal;

    private List<EgresoLineaResponse> egresos;
    private BigDecimal egresosTotal;

    private BigDecimal utilidad;
}
