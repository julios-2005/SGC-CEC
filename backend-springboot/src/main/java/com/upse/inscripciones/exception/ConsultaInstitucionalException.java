package com.upse.inscripciones.exception;

import org.springframework.http.HttpStatus;

/** Solo mensajes controlados: nunca propaga cuerpos, credenciales o URLs de TIC al navegador/log. */
public class ConsultaInstitucionalException extends RuntimeException {
    private final HttpStatus status;
    private final String codigo;

    public ConsultaInstitucionalException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() { return status; }
    public String getCodigo() { return codigo; }
}
