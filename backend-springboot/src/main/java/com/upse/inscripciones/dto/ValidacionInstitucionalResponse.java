package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.TipoUsuario;

/** Respuesta mínima: confirma solo la categoría solicitada; no expone el registro de TIC. */
public record ValidacionInstitucionalResponse(TipoUsuario tipoUsuario, boolean requiereConsulta,
        boolean verificado, String estado, String mensaje) {}
