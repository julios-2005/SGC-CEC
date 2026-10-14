package com.upse.inscripciones.dto;

import com.upse.inscripciones.entity.TipoUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ValidacionInstitucionalRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{5,20}", message = "El documento debe tener entre 5 y 20 letras o números.") String identificacion,
        @NotNull TipoUsuario tipoUsuario) {}
