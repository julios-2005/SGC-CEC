package com.upse.inscripciones.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private Boolean exito;
    private String mensaje;
    private UsuarioLogueadoDTO usuario;
    private Boolean debeCambiarContraseña;
    private String token;
    private String tipoToken;
    private Long expiraEnSegundos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UsuarioLogueadoDTO {
        private Long id;
        private String nombreUsuario;
        private String nombre; // Nombre completo del coordinador
        private String foto;
        private String rol;
    }
}
