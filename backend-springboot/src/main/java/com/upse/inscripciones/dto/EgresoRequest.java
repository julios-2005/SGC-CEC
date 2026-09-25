package com.upse.inscripciones.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record EgresoRequest(
        @NotBlank @Size(max = 100) String concepto,
        @NotNull @Min(1) @Max(1000000) Integer cantidad,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal valorUnitario) {}
