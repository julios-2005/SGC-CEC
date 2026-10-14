package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.LoginRequest;
import com.upse.inscripciones.dto.LoginResponse;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Valida las credenciales y retorna los datos del usuario logueado
     */
    public LoginResponse validarCredenciales(LoginRequest request) {
        String nombreUsuario = request.getUsuario() == null ? null : request.getUsuario().trim();
        Optional<Usuario> usuarioOpt = usuarioRepository.findByNombreUsuario(nombreUsuario);

        if (usuarioOpt.isEmpty()) {
            return LoginResponse.builder()
                    .exito(false)
                    .mensaje("Usuario o contraseña incorrectos")
                    .build();
        }

        Usuario usuario = usuarioOpt.get();

        // Verificar que el usuario esté activo
        if (!usuario.getEstado()) {
            return LoginResponse.builder()
                    .exito(false)
                    .mensaje("Este usuario ha sido desactivado. Contacte al administrador.")
                    .build();
        }

        // Validar contraseña con BCrypt
        if (!passwordEncoder.matches(request.getContraseña(), usuario.getContraseña())) {
            return LoginResponse.builder()
                    .exito(false)
                    .mensaje("Usuario o contraseña incorrectos")
                    .build();
        }

        // Login exitoso
        return LoginResponse.builder()
                .exito(true)
                .mensaje("Login exitoso")
                .debeCambiarContraseña(usuario.getDebeCambiarContraseña())
                .usuario(LoginResponse.UsuarioLogueadoDTO.builder()
                        .id(usuario.getId())
                        .nombreUsuario(usuario.getNombreUsuario())
                        .nombre(usuario.getNombreParaMostrar())
                        .foto(usuario.getFotoParaMostrar())
                        .rol(usuario.getRol().name())
                        .build())
                .build();
    }

    /**
     * Codifica una contraseña con BCrypt
     */
    public String encriptarContraseña(String contraseña) {
        validarFormatoContraseña(contraseña);
        return passwordEncoder.encode(contraseña);
    }

    /**
     * Valida que la contraseña cumpla los requisitos
     */
    public void validarFormatoContraseña(String contraseña) {
        if (contraseña == null || contraseña.length() < 8) {
            throw new ValidacionException("La contraseña debe tener al menos 8 caracteres");
        }

        if (!contraseña.matches(".*[A-Z].*")) {
            throw new ValidacionException("La contraseña debe contener al menos una mayúscula");
        }

        if (!contraseña.matches(".*[0-9].*")) {
            throw new ValidacionException("La contraseña debe contener al menos un número");
        }
    }

    /**
     * Verifica si una contraseña plana coincide con el hash
     */
    public boolean verificarContraseña(String contraseñaPlana, String contraseñaEnHash) {
        return passwordEncoder.matches(contraseñaPlana, contraseñaEnHash);
    }
}
