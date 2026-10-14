package com.upse.inscripciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioResponse {
    private Long id;
    private String nombreUsuario;
    private String rol;
    private Boolean estado;
    private Boolean debeCambiarContraseña;

    // Nombre/foto genéricos del usuario, resueltos independientemente de si
    // tiene coordinador o no (ver Usuario.getNombreParaMostrar()/
    // getFotoParaMostrar()). Nunca vienen vacíos: caen de vuelta a
    // nombreCompleto y luego a nombreUsuario si no hay coordinador.
    private String nombre;
    private String foto;

    // Info del coordinador — NULL cuando el usuario no tiene coordinador
    // asociado (ej. un ADMIN_GENERAL). El frontend debe manejar ese caso
    // mostrando "Sin coordinador" en vez de "null null".
    private Long coordinadorId;
    private String coordinadorNombre;
    private String coordinadorApellido;
    private String coordinadorFoto;
    private String coordinadorCorreo;

    // Para mostrar al crear/restablecer contraseña
    private String contraseñaTemporal;
}
