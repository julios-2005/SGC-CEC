package com.upse.inscripciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestablecerContrasenaResponse {
    private Boolean exito;
    private String mensaje;
    private String contraseñaTemporal;
}
