package com.upse.inscripciones.entity;

public enum Rol {
    ADMIN_GENERAL("Administrador General"),
    COORDINADOR("Coordinador"),
    COBROS("Cobros");

    private final String descripcion;

    Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
