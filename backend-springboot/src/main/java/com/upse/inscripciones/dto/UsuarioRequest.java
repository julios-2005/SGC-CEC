package com.upse.inscripciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Los límites de @Size replican el largo de columna definido en
 * {@link com.upse.inscripciones.entity.Usuario}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioRequest {

    @NotBlank(message = "El nombre de usuario es requerido")
    @Size(max = 50, message = "El nombre de usuario no puede superar los 50 caracteres.")
    private String nombreUsuario;

    // Opcional a nivel de Bean Validation a propósito: si es obligatorio o
    // no depende del rol (COORDINADOR sí lo exige, ADMIN_GENERAL no), y esa
    // regla vive en UsuarioService porque cruza dos campos del mismo
    // request — Bean Validation por sí solo no puede expresar "obligatorio
    // solo si rol == X" de forma limpia.
    private Long idCoordinador;

    @NotBlank(message = "El rol es requerido")
    @Size(max = 20, message = "El rol no es válido.")
    private String rol; // "ADMIN_GENERAL" o "COORDINADOR"

    @Builder.Default
    private Boolean estado = true;

    // Solo se usa (y solo tiene sentido) cuando el usuario NO tiene
    // coordinador, típicamente ADMIN_GENERAL. Si se manda igual estando
    // asociado a un coordinador, el service la ignora: el nombre del
    // coordinador siempre es la fuente de verdad cuando existe.
    @Size(max = 150, message = "El nombre completo no puede superar los 150 caracteres.")
    private String nombreCompleto;
}
