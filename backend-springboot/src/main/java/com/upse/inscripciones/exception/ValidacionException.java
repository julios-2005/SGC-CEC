package com.upse.inscripciones.exception;

/**
 * Excepción para validaciones fallidas (400)
 */
public class ValidacionException extends RuntimeException {
    public ValidacionException(String mensaje) {
        super(mensaje);
    }

    public ValidacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
