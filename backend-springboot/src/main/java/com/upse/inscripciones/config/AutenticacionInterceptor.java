package com.upse.inscripciones.config;

import com.upse.inscripciones.service.JwtService;
import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.repository.UsuarioRepository;
import com.upse.inscripciones.util.RequestAuthUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriUtils;

import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * Protege la API REST mediante JWT. Las únicas excepciones son el catálogo
 * público, sus fotografías y el POST del formulario de inscripción.
 */
public class AutenticacionInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/api/auth/login",
            "/api/planificaciones/vigentes",
            "/api/cursos/archivos/**",
            "/api/descuentos/activos"
    );
    private static final List<String> RUTAS_ADMIN = List.of(
            "/api/usuarios",
            "/api/usuarios/**"
    );

    // El rol COBROS solo puede consultar el listado/detalle de inscripciones
    // y descargar los documentos adjuntos (cédula, comprobante de pago,
    // documento adicional, comprobante de matrícula). No puede aceptar,
    // rechazar ni anular inscripciones, ni acceder a ningún otro módulo.
    private static final List<String> RUTAS_COBROS_GET = List.of(
            "/api/inscripciones",
            "/api/inscripciones/archivos/**"
    );

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public AutenticacionInterceptor(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String ruta = UriUtils.decode(request.getRequestURI().substring(request.getContextPath().length()), StandardCharsets.UTF_8);
        String metodo = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(metodo) || esPublica(ruta, metodo)) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            responder(response, HttpServletResponse.SC_UNAUTHORIZED, "No autorizado");
            return false;
        }

        try {
            Claims claims = jwtService.validarYLeer(authorization.substring(7));
            Usuario usuario = usuarioRepository.findById(Long.valueOf(claims.getSubject())).orElse(null);
            if (usuario == null || !Boolean.TRUE.equals(usuario.getEstado())) {
                responder(response, HttpServletResponse.SC_UNAUTHORIZED, "La cuenta está desactivada o ya no existe");
                return false;
            }
            // El rol y el estado actuales prevalecen sobre un token emitido horas antes.
            request.setAttribute(RequestAuthUtils.USUARIO_ID, usuario.getId());
            request.setAttribute(RequestAuthUtils.USUARIO, usuario.getNombreUsuario());
            request.setAttribute(RequestAuthUtils.NOMBRE, usuario.getNombreParaMostrar());
            request.setAttribute(RequestAuthUtils.FOTO, usuario.getFotoParaMostrar());
            request.setAttribute(RequestAuthUtils.ROL, usuario.getRol().name());
            if (Boolean.TRUE.equals(usuario.getDebeCambiarContraseña()) && !ruta.startsWith("/api/auth/")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"exito\":false,\"codigo\":\"CAMBIO_CONTRASENA_REQUERIDO\",\"mensaje\":\"Debes cambiar tu contraseña temporal antes de continuar\"}");
                return false;
            }
        } catch (JwtException | IllegalArgumentException ex) {
            responder(response, HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o vencido");
            return false;
        }

        boolean requiereAdmin = RUTAS_ADMIN.stream().anyMatch(pattern -> MATCHER.match(pattern, ruta));
        if (requiereAdmin && !RequestAuthUtils.esAdminGeneral(request)) {
            responder(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado");
            return false;
        }

        if (RequestAuthUtils.esCobros(request) && !ruta.startsWith("/api/auth/")
                && !esRutaPermitidaParaCobros(ruta, metodo)) {
            responder(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado");
            return false;
        }
        return true;
    }

    private boolean esPublica(String ruta, String metodo) {
        if (RUTAS_PUBLICAS.stream().anyMatch(pattern -> MATCHER.match(pattern, ruta))) return true;
        return "/api/inscripciones".equals(ruta) && "POST".equalsIgnoreCase(metodo);
    }

    /**
     * El rol COBROS solo puede leer (GET) el listado de inscripciones, el
     * detalle de una en concreto y los archivos adjuntos que suben los
     * estudiantes al inscribirse. No puede exportar, filtrar por
     * planificación, ni mucho menos aceptar/rechazar/anular (PATCH), que
     * ya queda excluido por no ser GET.
     */
    private boolean esRutaPermitidaParaCobros(String ruta, String metodo) {
        if (!"GET".equalsIgnoreCase(metodo)) return false;
        if (RUTAS_COBROS_GET.stream().anyMatch(pattern -> MATCHER.match(pattern, ruta))) return true;
        return ruta.matches("/api/inscripciones/\\d+");
    }

    private void responder(HttpServletResponse response, int status, String mensaje) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"exito\":false,\"mensaje\":\"" + mensaje + "\"}");
    }
}
