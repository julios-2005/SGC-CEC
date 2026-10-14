package com.upse.inscripciones.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * {@link CsrfTokenRepository} guarda el token como un {@code Supplier}
 * diferido (para no generar/escribir la cookie en peticiones que nunca la
 * necesitan). El problema práctico para un frontend que no renderiza
 * ningún formulario del lado del servidor (todo HTML estático + fetch) es
 * que, sin forzar la resolución de ese supplier al menos una vez, la
 * cookie XSRF-TOKEN nunca se termina de escribir y el navegador nunca la
 * recibe. Este filtro (parte del patrón oficial recomendado por Spring
 * Security para SPAs) simplemente llama a {@code csrfToken.getToken()} en
 * cada request para forzar esa resolución, garantizando que la cookie
 * quede seteada desde la primera petición GET de cualquier página.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
