package com.upse.inscripciones.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * {@link CsrfTokenRequestHandler} para un frontend que envía el token CSRF
 * en el header {@code X-XSRF-TOKEN} (leído directamente de la cookie
 * XSRF-TOKEN por JS, ver app-compartida.js) en vez de como parámetro de un
 * formulario HTML renderizado por el servidor.
 * <p>
 * Estructura idéntica a la que documenta la propia guía oficial de Spring
 * Security para proteger SPAs con CSRF (delegar el guardado/rotación del
 * token a {@link XorCsrfTokenRequestAttributeHandler} tal cual, y solo
 * personalizar de dónde se LEE el token entrante en cada
 * POST/PUT/PATCH/DELETE: primero el header X-XSRF-TOKEN y, si no viene,
 * el comportamiento por defecto de {@link CsrfTokenRequestAttributeHandler}
 * — un frontend puramente JS como este nunca manda el token como
 * parámetro de formulario, así que en la práctica siempre se resuelve por
 * el header).
 */
public final class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    private final CsrfTokenRequestHandler delegado = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.delegado.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String valorHeader = request.getHeader(csrfToken.getHeaderName());
        return StringUtils.hasText(valorHeader)
                ? valorHeader
                : super.resolveCsrfTokenValue(request, csrfToken);
    }
}
