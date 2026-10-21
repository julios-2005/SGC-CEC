package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CambioContrasenaRequest;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CambioContrasenaService {

    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;

    /**
     * Cambia la contraseña del usuario logueado
     */
    public Usuario cambiarContraseña(Long usuarioId, CambioContrasenaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Validar que la contraseña actual sea correcta
        if (!authService.verificarContraseña(request.getContraseñaActual(), usuario.getContraseña())) {
            throw new ValidacionException("La contraseña actual es incorrecta");
        }

        // Validar que las nuevas contraseñas coincidan
        if (!request.getContraseñaNueva().equals(request.getConfirmarContraseña())) {
            throw new ValidacionException("Las contraseñas nuevas no coinciden");
        }

        // Validar que la nueva contraseña sea diferente a la actual
        if (authService.verificarContraseña(request.getContraseñaNueva(), usuario.getContraseña())) {
            throw new ValidacionException("La nueva contraseña debe ser diferente a la actual");
        }

        // Validar formato de la nueva contraseña
        authService.validarFormatoContraseña(request.getContraseñaNueva());

        // Actualizar contraseña
        usuario.setContraseña(authService.encriptarContraseña(request.getContraseñaNueva()));
        usuario.setDebeCambiarContraseña(false); // Ya no necesita cambiarla
        invalidarSesionesAnteriores(usuario);
        usuarioRepository.save(usuario);
        return usuario;
    }

    /**
     * Cambia la contraseña obligatoriamente (en primer login)
     */
    public Usuario cambiarContraseñaObligatoria(Long usuarioId, CambioContrasenaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        // Validar que la contraseña actual sea correcta (la temporal)
        if (!authService.verificarContraseña(request.getContraseñaActual(), usuario.getContraseña())) {
            throw new ValidacionException("La contraseña temporal es incorrecta");
        }

        // Validar que las nuevas contraseñas coincidan
        if (!request.getContraseñaNueva().equals(request.getConfirmarContraseña())) {
            throw new ValidacionException("Las contraseñas nuevas no coinciden");
        }

        if (authService.verificarContraseña(request.getContraseñaNueva(), usuario.getContraseña())) {
            throw new ValidacionException("La nueva contraseña debe ser diferente a la temporal");
        }

        // Validar formato de la nueva contraseña
        authService.validarFormatoContraseña(request.getContraseñaNueva());

        // Actualizar contraseña
        usuario.setContraseña(authService.encriptarContraseña(request.getContraseñaNueva()));
        usuario.setDebeCambiarContraseña(false);
        invalidarSesionesAnteriores(usuario);
        usuarioRepository.save(usuario);
        return usuario;
    }

    /**
     * Un cambio de contraseña cierra todas las sesiones abiertas con la anterior
     * (por ejemplo, un token robado): sube la versión que exige el filtro JWT.
     * La sesión actual recibe un token nuevo en la respuesta del controlador.
     */
    private void invalidarSesionesAnteriores(Usuario usuario) {
        int actual = usuario.getVersionToken() == null ? 0 : usuario.getVersionToken();
        usuario.setVersionToken(actual + 1);
    }
}
