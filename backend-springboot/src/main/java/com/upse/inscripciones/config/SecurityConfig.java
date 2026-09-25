package com.upse.inscripciones.config;

import com.upse.inscripciones.service.JwtService;
import com.upse.inscripciones.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Configuración de seguridad real de Spring Security (autenticación +
 * autorización), en vez del esquema anterior de
 * "SecurityFilterChain permite todo + un HandlerInterceptor de MVC decide".
 * <p>
 * JwtAuthenticationFilter solo autentica (quién es, sigue activo). La
 * autorización — qué puede hacer cada rol — se reparte así:
 * <ul>
 *   <li>Aquí, a nivel de ruta, el rol COBROS como lista blanca con
 *       defecto-denegar (solo lectura de inscripciones y sus archivos, más
 *       /api/auth/**). Es una lista blanca a propósito: un endpoint o
 *       controller nuevo queda cerrado para COBROS sin que nadie tenga que
 *       acordarse de bloquearlo.</li>
 *   <li>Con @PreAuthorize en el propio método del controller, las
 *       restricciones específicas de los demás roles (ver
 *       CoordinadorController, UsuarioController, InscripcionController).</li>
 * </ul>
 * AutorizacionPorRolTest recorre TODOS los endpoints registrados y verifica
 * que COBROS no llegue a ninguno fuera de su lista blanca.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    /** La documentación OpenAPI solo es pública mientras está habilitada (SWAGGER_ENABLED=true). */
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean documentacionApiHabilitada;

    /**
     * Permite percent-encoding inocuo de caracteres de la ruta (por ejemplo,
     * %75 == 'u') para que Spring Security pueda normalizar la URI y aplicar
     * las mismas reglas de autorización que a la ruta escrita normalmente.
     *
     * No se habilitan encoded slash/backslash, que sí podrían alterar la
     * segmentación de la ruta.
     */
    @Bean
    public HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedPercent(true);
        return firewall;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, usuarioRepository, documentacionApiHabilitada),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    if (documentacionApiHabilitada) {
                        auth.requestMatchers(HttpMethod.GET,
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth
                        // El preflight CORS no manda Authorization; Spring ya lo resuelve
                        // vía CorsConfigurationSource antes de llegar aquí.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/planificaciones/vigentes").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cursos/archivos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/descuentos/activos").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/inscripciones").permitAll()
                        // Estas rutas deben rechazarse en el filtro (antes de que
                        // @Valid pueda convertir una petición de un rol sin permiso
                        // en un 400). Así un usuario autenticado con otro rol recibe
                        // siempre 403, incluso si el body es incompleto.
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").hasRole("ADMIN_GENERAL")
                        .requestMatchers(HttpMethod.PUT, "/api/usuarios/{id}").hasRole("ADMIN_GENERAL")
                        .requestMatchers(HttpMethod.POST, "/api/coordinadores").hasRole("ADMIN_GENERAL")
                        .requestMatchers(HttpMethod.PUT, "/api/coordinadores/{id}").hasRole("ADMIN_GENERAL")
                        .requestMatchers(HttpMethod.PATCH, "/api/coordinadores/{id}/estado").hasRole("ADMIN_GENERAL")
                        .requestMatchers(HttpMethod.DELETE, "/api/coordinadores/{id}").hasRole("ADMIN_GENERAL")
                        // Cualquier usuario con sesión (incluido COBROS) puede usar los
                        // endpoints de autenticación: sesión actual, logout y cambio de
                        // contraseña (la obligatoria incluida).
                        .requestMatchers("/api/auth/**").authenticated()
                        // COBROS es una LISTA BLANCA: solo puede LEER el listado y el
                        // detalle de inscripciones y descargar sus adjuntos. Estas rutas
                        // quedan abiertas a cualquier rol autenticado (las restricciones
                        // adicionales por rol están en @PreAuthorize).
                        .requestMatchers(HttpMethod.GET,
                                "/api/inscripciones",
                                "/api/inscripciones/{id:\\d+}",
                                "/api/inscripciones/archivos/**").authenticated()
                        // Regla base por DEFECTO-DENEGAR para COBROS: todo lo que no esté
                        // arriba le queda prohibido, incluidos los controllers y endpoints
                        // que se agreguen en el futuro. No depende de que cada método
                        // recuerde llevar su propio @PreAuthorize.
                        .anyRequest().access(SecurityConfig::autenticadoYNoCobros);
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(SecurityConfig::noAutorizado)
                        .accessDeniedHandler((request, response, ex) -> escribirJson(response, 403, "Acceso denegado")));
        return http.build();
    }

    /**
     * Exige sesión válida y que el rol NO sea COBROS. Se usa como regla base
     * (anyRequest) para que COBROS solo llegue a lo que se le permite
     * explícitamente más arriba. Una petición anónima nunca pasa por aquí con
     * éxito: se rechaza igual que en authenticated().
     */
    private static AuthorizationDecision autenticadoYNoCobros(
            Supplier<Authentication> autenticacion, RequestAuthorizationContext contexto) {
        Authentication actual = autenticacion.get();
        boolean autenticado = actual != null && actual.isAuthenticated()
                && !(actual instanceof AnonymousAuthenticationToken);
        boolean esCobros = autenticado && actual.getAuthorities().stream()
                .anyMatch(autoridad -> "ROLE_COBROS".equals(autoridad.getAuthority()));
        return new AuthorizationDecision(autenticado && !esCobros);
    }

    private static void noAutorizado(jakarta.servlet.http.HttpServletRequest request,
                                      jakarta.servlet.http.HttpServletResponse response,
                                      org.springframework.security.core.AuthenticationException ex) throws IOException {
        // En la práctica JwtAuthenticationFilter ya responde 401 antes de
        // llegar aquí; esto es una red de seguridad por si alguna vez una
        // ruta queda marcada authenticated() sin pasar por el filtro.
        escribirJson(response, 401, "No autorizado");
    }

    private static void escribirJson(jakarta.servlet.http.HttpServletResponse response, int status, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"exito\":false,\"mensaje\":\"" + mensaje + "\"}");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
