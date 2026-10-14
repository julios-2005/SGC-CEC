package com.upse.inscripciones.util;

import jakarta.servlet.http.HttpSession;

/**
 * Acceso centralizado y seguro a los atributos de la sesión HTTP.
 * <p>
 * Antes cada controller hacía su propio cast directo, ej.
 * {@code (Long) session.getAttribute("usuarioId")}. Si algún día el tipo
 * guardado cambiara (o llegara a faltar por cualquier motivo), ese cast
 * lanza un ClassCastException/NullPointerException que cae en el manejador
 * de errores 500 genérico en vez de tratarse como "no autenticado". Los
 * métodos de aquí devuelven null / false en vez de lanzar, para que cada
 * llamador decida cómo reaccionar (ej. responder 401).
 */
public final class SesionUtils {

    private SesionUtils() {
    }

    public static boolean estaLogueado(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object valor = session.getAttribute("logueado");
        return valor instanceof Boolean && (Boolean) valor;
    }

    public static Long obtenerUsuarioId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object valor = session.getAttribute("usuarioId");
        return valor instanceof Long ? (Long) valor : null;
    }

    public static String obtenerRol(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object valor = session.getAttribute("rol");
        return valor instanceof String ? (String) valor : null;
    }

    public static String obtenerNombreCompleto(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object valor = session.getAttribute("nombreCompleto");
        return valor instanceof String ? (String) valor : null;
    }

    public static String obtenerFoto(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object valor = session.getAttribute("foto");
        return valor instanceof String ? (String) valor : null;
    }

    public static boolean esAdminGeneral(HttpSession session) {
        return "ADMIN_GENERAL".equals(obtenerRol(session));
    }
}
