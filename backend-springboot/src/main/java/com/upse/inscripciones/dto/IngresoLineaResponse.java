package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.TipoUsuario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngresoLineaResponse {
    private Long id;                 // necesario para editar la cantidad de la línea
    private TipoUsuario tipoUsuario;
    private String detalle;          // etiqueta a mostrar (ej: "EXTERNOS")
    private Integer cantidad;
    private BigDecimal valorUnitario;
    private BigDecimal total;
    /** true si la cantidad se puede editar (la línea tiene valor por participante definido). */
    private boolean editable;
    /** true si la cantidad actual fue ingresada a mano y no viene del conteo de inscripciones. */
    private boolean cantidadManual;
}
