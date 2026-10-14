package com.upse.inscripciones.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record GastoCecRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal importe,
        @NotNull Long version) {}
