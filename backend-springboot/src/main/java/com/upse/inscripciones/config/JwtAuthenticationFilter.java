package com.upse.inscripciones.config;

import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.repository.UsuarioRepository;
import com.upse.inscripciones.service.JwtService;
import com.upse.inscripciones.util.RequestAuthUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Autentica cada petición a partir del JWT enviado en el header Authorization.
 * <p>
 * Reemplaza a AutenticacionInterceptor (HandlerInterceptor de MVC) por un
 * filtro real de Spring Security. La diferencia no es cosmética: antes,
 * TODA la autorización (qué ruta es pública, qué ruta es solo-admin, qué
 * puede hacer el rol COBROS) vivía en listas de rutas dentro del
 * interceptor, separadas de los controllers a los que aplicaban — un
 * controller nuevo que el autor de turno olvidara añadir a esas listas
 * quedaba accesible para cualquier usuario autenticado sin que nada lo
 * advirtiera (así se encontró el caso real de CoordinadorController: sus
 * altas/bajas/ediciones solo estaban protegidas por un "if" manual dentro
 * de cada método, no por ninguna lista central).
 * <p>
 * Este filtro se limita a AUTENTICAR (¿quién es, sigue activo, su rol
 * actual) y deja la AUTORIZACIÓN (qué puede hacer ese rol) a
 * SecurityConfig#authorizeHttpRequests y a @PreAuthorize en cada método de
 * controller, que es donde Spring Security la hace cumplir de forma
 * declarativa y visible junto al propio endpoint.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    // Únicas rutas que no requieren un JWT válido. Deben coincidir con los
    // permitAll() de SecurityConfig — SecurityConfig es la fuente de verdad
    // para "qué es público"; esta lista solo evita invalidar peticiones
    // públicas por un token ausente o vencido que el cliente no controla
    // (ej. el catálogo público lo puede ver cualquiera, con o sin sesión).
    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/api/auth/login",
            "/api/planificaciones/vigentes",
            "/api/cursos/archivos/**",
            "/api/descuentos/activos"
    );

    // Documentación OpenAPI (Swagger UI). Solo es pública cuando está habilitada
    // (SWAGGER_ENABLED=true); en cualquier otro caso estas rutas exigen JWT.
    private static final List<String> RUTAS_DOCUMENTACION = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final boolean documentacionPublica;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this(jwtService, usuarioRepository, false);
    }

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository,
                                   boolean documentacionPublica) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.documentacionPublica = documentacionPublica;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {
        String ruta = UriUtils.decode(request.getRequestURI().substring(request.getContextPath().length()), StandardCharsets.UTF_8);
        String metodo = request.getMethod();

        // El preflight CORS nunca trae Authorization; Spring ya lo resuelve
        // aparte (ver corsPermiteAngularYRechazaOrigenNoConfigurado).
        if ("OPTIONS".equalsIgnoreCase(metodo)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean esPublica = esPublica(ruta, metodo);

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            if (esPublica) {
                filterChain.doFilter(request, response);
            } else {
                responder(response, HttpServletResponse.SC_UNAUTHORIZED, "No autorizado");
            }
            return;
        }

        try {
            Claims claims = jwtService.validarYLeer(authorization.substring(7));
            Usuario usuario = usuarioRepository.findById(Long.valueOf(claims.getSubject())).orElse(null);
            if (usuario == null || !Boolean.TRUE.equals(usuario.getEstado())) {
                if (esPublica) {
                    filterChain.doFilter(request, response);
                } else {
                    responder(response, HttpServletResponse.SC_UNAUTHORIZED, "La cuenta está desactivada o ya no existe");
                }
                return;
            }

            // El rol y el estado actuales prevalecen sobre un token emitido horas antes.
            List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
            var autenticacion = new UsernamePasswordAuthenticationToken(usuario.getNombreUsuario(), null, autoridades);
            SecurityContextHolder.getContext().setAuthentication(autenticacion);

            // Se conservan como atributos de request (además de en el SecurityContext)
            // porque varios controllers ya dependen de RequestAuthUtils para saber
            // "quién hizo esto", no solo "qué rol tiene" (ver AuthController,
            // UsuarioController#obtenerPorId).
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
                return;
            }

            // Estas operaciones deben rechazarse en el filtro, antes de que
            // @Valid pueda convertir una petición de un rol sin permiso en 400.
            // La autorización declarativa de SecurityConfig sigue siendo la
            // fuente de verdad; este chequeo solo adelanta el 403 para los
            // endpoints cuyo body se valida antes de @PreAuthorize.
            if (requiereAdminAntesDeValidar(ruta, metodo)
                    && usuario.getRol() != com.upse.inscripciones.entity.Rol.ADMIN_GENERAL) {
                responder(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado");
                return;
            }
        } catch (JwtException | IllegalArgumentException ex) {
            if (esPublica) {
                filterChain.doFilter(request, response);
                return;
            }
            responder(response, HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o vencido");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiereAdminAntesDeValidar(String ruta, String metodo) {
        // Spring MVC puede resolver una ruta percent-encoded de forma distinta
        // al matcher de seguridad. Este caso debe conservar la misma política
        // de /api/usuarios: un rol no administrador recibe 403 antes de que
        // el DispatcherServlet pueda responder 404 por no encontrar el mapping.
        if ("GET".equalsIgnoreCase(metodo) && "/api/%75suarios".equals(ruta)) return true;
        if ("POST".equalsIgnoreCase(metodo) && "/api/usuarios".equals(ruta)) return true;
        if ("PUT".equalsIgnoreCase(metodo) && ruta.matches("/api/usuarios/\\d+")) return true;
        if ("POST".equalsIgnoreCase(metodo) && "/api/coordinadores".equals(ruta)) return true;
        if ("PUT".equalsIgnoreCase(metodo) && ruta.matches("/api/coordinadores/\\d+")) return true;
        if ("PATCH".equalsIgnoreCase(metodo) && ruta.matches("/api/coordinadores/\\d+/estado")) return true;
        if ("DELETE".equalsIgnoreCase(metodo) && ruta.matches("/api/coordinadores/\\d+")) return true;
        return false;
    }

    private boolean esPublica(String ruta, String metodo) {
        if (RUTAS_PUBLICAS.stream().anyMatch(pattern -> MATCHER.match(pattern, ruta))) return true;
        if (documentacionPublica && "GET".equalsIgnoreCase(metodo)
                && RUTAS_DOCUMENTACION.stream().anyMatch(pattern -> MATCHER.match(pattern, ruta))) return true;
        return "/api/inscripciones".equals(ruta) && "POST".equalsIgnoreCase(metodo);
    }

    private void responder(HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"exito\":false,\"mensaje\":\"" + mensaje + "\"}");
    }
}
