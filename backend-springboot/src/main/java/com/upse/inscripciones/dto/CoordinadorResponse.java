package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Coordinador;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoordinadorResponse {

    private Long id;
    private String cedula;
    private String nombres;
    private String apellidos;
    private String telefono;
    private String correo;
    private String foto;
    private Boolean estado;

    public static CoordinadorResponse fromEntity(Coordinador c) {
        return CoordinadorResponse.builder()
                .id(c.getId())
                .cedula(c.getCedula())
                .nombres(c.getNombres())
                .apellidos(c.getApellidos())
                .telefono(c.getTelefono())
                .correo(c.getCorreo())
                .foto(c.getFoto())
                .estado(c.getEstado())
                .build();
    }
}
