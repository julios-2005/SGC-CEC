package com.upse.inscripciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EgresoLineaResponse {
    private Long id;              // null para la fila fija "Especialista" (no editable)
    private String concepto;
    private Integer cantidad;
    private BigDecimal valorUnitario;
    private BigDecimal total;
    private boolean editable;     // el frontend solo muestra botones editar/eliminar si es true
}
