package com.upse.inscripciones.util;

import com.upse.inscripciones.exception.ValidacionException;

import java.util.regex.Pattern;

/**
 * Normaliza números de teléfono/WhatsApp a formato internacional E.164
 * (ej. +593991234567), para poder generar enlaces de WhatsApp de forma
 * confiable y aceptar tanto números ecuatorianos como de otros países.
 * <p>
 * Reglas:
 * - Si el usuario ya escribió el "+" con código de país, se respeta tal cual
 *   (solo se limpian espacios/guiones).
 * - Si NO escribió "+", se asume Ecuador (+593) y, si el número empieza con
 *   "0" (formato local, ej. 0991234567), se descarta ese cero inicial antes
 *   de anteponer +593 (0991234567 -> +593991234567).
 */
public final class TelefonoUtils {

    // E.164: '+' seguido de 8 a 15 dígitos en total.
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private TelefonoUtils() {
    }

    /**
     * Normaliza el texto ingresado por el usuario a formato E.164.
     * Lanza ValidacionException si, después de normalizar, el resultado no
     * es un número internacional válido.
     */
    public static String normalizar(String telefonoIngresado) {
        if (telefonoIngresado == null || telefonoIngresado.isBlank()) {
            throw new ValidacionException("El Número de WhatsApp es obligatorio.");
        }

        // Quita todo lo que no sea dígito o "+" (espacios, guiones, paréntesis).
        String limpio = telefonoIngresado.trim().replaceAll("[^\\d+]", "");

        String normalizado;
        if (limpio.startsWith("+")) {
            normalizado = limpio;
        } else if (limpio.startsWith("00")) {
            // Prefijo internacional alternativo (00593... == +593...)
            normalizado = "+" + limpio.substring(2);
        } else {
            // Sin código de país: se asume Ecuador. Un "0" inicial es el
            // prefijo local ecuatoriano (0991234567) y se descarta.
            String sinCeroInicial = limpio.startsWith("0") ? limpio.substring(1) : limpio;
            normalizado = "+593" + sinCeroInicial;
        }

        if (!E164_PATTERN.matcher(normalizado).matches()) {
            throw new ValidacionException(
                    "El Número de WhatsApp no es válido. Ingréselo incluyendo el código de país, ej.: +593 99 123 4567.");
        }

        return normalizado;
    }
}
