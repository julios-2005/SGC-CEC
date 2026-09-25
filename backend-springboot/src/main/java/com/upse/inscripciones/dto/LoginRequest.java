package com.upse.inscripciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sin límite de longitud aquí, un intento de login con un "usuario" o
 * "contraseña" de miles de caracteres llega igual hasta AuthService (y, en
 * el caso de la contraseña, hasta el cálculo de BCrypt) antes de ser
 * rechazado — un vector barato de esfuerzo de CPU. usuario replica el largo
 * de columna de {@link com.upse.inscripciones.entity.Usuario#nombreUsuario};
 * contraseña usa el mismo tope defensivo de 100 que CambioContrasenaRequest
 * (no hay columna que limite la contraseña en texto plano porque nunca se
 * guarda como tal).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {
    
    @NotBlank(message = "El usuario es requerido")
    @Size(max = 50, message = "El usuario no es válido.")
    private String usuario;
    
    @NotBlank(message = "La contraseña es requerida")
    @Size(max = 100, message = "La contraseña no es válida.")
    private String contraseña;
}
