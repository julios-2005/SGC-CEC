package com.upse.inscripciones.entity;

/**
 * Tipos de participante que pueden inscribirse a un curso.
 *
 * Todos los tipos, EXCEPTO EXTERNO, requieren adjuntar un documento de
 * respaldo (documentoAdicional) en el formulario de inscripcion.
 */
public enum TipoUsuario {
    EXTERNO("Externo"),
    ESTUDIANTE_UPSE("Estudiante Upse"),
    DOCENTE_UPSE("Docente Upse"),
    ADMINISTRATIVO_UPSE("Administrativo Upse"),
    MAESTRANDO("Maestrando"),
    GRADUADO("Graduado Upse -3 Nivel"),
    PERSONA_DISCAPACIDAD("Persona con discapacidad");

    private final String descripcion;

    TipoUsuario(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Nombre del documento de respaldo que se le pide a cada tipo (para mostrar en el form). */
    public String getEtiquetaDocumento() {
        return switch (this) {
            case ESTUDIANTE_UPSE -> "Comprobante de Matrícula";
            case PERSONA_DISCAPACIDAD -> "Carnet de Discapacidad";
            case DOCENTE_UPSE -> "Nombramiento o Contrato Docente UPSE";
            case ADMINISTRATIVO_UPSE -> "Nombramiento o Contrato Administrativo UPSE";
            case MAESTRANDO -> "Certificado de Maestría en Curso";
            case GRADUADO -> "Certificado de Título / Graduado UPSE";
            default -> null; // EXTERNO no requiere documento adicional
        };
    }

    public boolean requiereDocumentoAdicional() {
        return this != EXTERNO;
    }

    /**
     * Docente Upse y Administrativo Upse SÍ pueden adjuntar su documento de
     * respaldo (nombramiento/contrato), pero no es obligatorio: algunas
     * dependencias no les entregan ese documento. Para el resto de tipos
     * (que sí lo requieren) sigue siendo obligatorio.
     */
    public boolean esDocumentoAdicionalOpcional() {
        return this == DOCENTE_UPSE || this == ADMINISTRATIVO_UPSE;
    }
}
