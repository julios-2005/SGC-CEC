package com.upse.inscripciones.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Datos del usuario autenticado que JwtService coloca en la petición.
 * Mantiene a los controllers independientes de la implementación del token.
 */
public final class RequestAuthUtils {

    public static final String USUARIO_ID = "auth.usuarioId";
    public static final String USUARIO = "auth.usuario";
    public static final String NOMBRE = "auth.nombre";
    public static final String FOTO = "auth.foto";
    public static final String ROL = "auth.rol";

    private RequestAuthUtils() {
    }

    public static Long obtenerUsuarioId(HttpServletRequest request) {
        Object valor = request.getAttribute(USUARIO_ID);
        return valor instanceof Long ? (Long) valor : null;
    }

    public static String obtenerRol(HttpServletRequest request) {
        Object valor = request.getAttribute(ROL);
        return valor instanceof String ? (String) valor : null;
    }

    public static String obtenerNombre(HttpServletRequest request) {
        Object valor = request.getAttribute(NOMBRE);
        return valor instanceof String ? (String) valor : null;
    }

    public static String obtenerFoto(HttpServletRequest request) {
        Object valor = request.getAttribute(FOTO);
        return valor instanceof String ? (String) valor : null;
    }

    public static boolean esAdminGeneral(HttpServletRequest request) {
        return "ADMIN_GENERAL".equals(obtenerRol(request));
    }

    public static boolean esCobros(HttpServletRequest request) {
        return "COBROS".equals(obtenerRol(request));
    }
}
