package com.upse.inscripciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * El límite superior de @Size en las contraseñas no viene de una columna de
 * BD (la contraseña nunca se guarda en texto plano, solo su hash BCrypt),
 * sino de una precaución independiente: BCrypt trunca silenciosamente
 * cualquier entrada más larga de 72 bytes, así que sin un tope explícito
 * aquí un usuario podría "creer" que cambió su contraseña a un valor largo
 * cuando en realidad solo los primeros 72 bytes importan. 100 caracteres da
 * margen de sobra y evita esa confusión.
 * <p>
 * El mínimo de 8 en contraseñaNueva replica (no reemplaza) la regla que ya
 * aplica {@code AuthService.validarFormatoContraseña()} — se deja aquí
 * también para que Bean Validation la rechace con el mismo umbral antes de
 * llegar al service, sin cambiar el mensaje de error que ya conocía el
 * frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CambioContrasenaRequest {
    
    @NotBlank(message = "La contraseña actual es requerida")
    @Size(max = 100, message = "La contraseña actual no puede superar los 100 caracteres.")
    private String contraseñaActual;
    
    @NotBlank(message = "La contraseña nueva es requerida")
    @Size(min = 8, max = 100, message = "La contraseña nueva debe tener entre 8 y 100 caracteres.")
    private String contraseñaNueva;
    
    @NotBlank(message = "La confirmación de contraseña es requerida")
    @Size(max = 100, message = "La confirmación de contraseña no puede superar los 100 caracteres.")
    private String confirmarContraseña;
}
