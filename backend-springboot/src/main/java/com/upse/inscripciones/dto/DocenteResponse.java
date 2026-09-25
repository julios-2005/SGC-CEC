package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.Especialista;

public record DocenteResponse(Long id, String nombres, String apellidos, String paisNacionalidad) {
    public static DocenteResponse fromEntity(Especialista e) {
        return new DocenteResponse(e.getId(), e.getNombres(), e.getApellidos(), e.getPaisNacionalidad());
    }
}
