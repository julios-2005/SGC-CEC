package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Descuento;
import com.upse.inscripciones.entity.TipoUsuario;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DescuentoResponse {

    private Long id;
    private String nombre;
    private TipoUsuario tipoUsuario;
    private String tipoUsuarioDescripcion;
    private BigDecimal porcentaje;
    private Boolean estado;

    public static DescuentoResponse fromEntity(Descuento d) {
        return DescuentoResponse.builder()
                .id(d.getId())
                .nombre(d.getNombre())
                .tipoUsuario(d.getTipoUsuario())
                .tipoUsuarioDescripcion(d.getTipoUsuario() != null ? d.getTipoUsuario().getDescripcion() : null)
                .porcentaje(d.getPorcentaje())
                .estado(d.getEstado())
                .build();
    }
}
