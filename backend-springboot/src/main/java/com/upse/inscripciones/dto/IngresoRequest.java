package com.upse.inscripciones.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Edición manual de una línea de ingreso del Informe Económico: SOLO viaja la
 * cantidad de participantes. El valor por participante no se recibe del
 * cliente a propósito — se conserva el que ya tiene la línea, que sale del
 * costo del curso y del descuento de ese tipo de participante — y el total
 * se recalcula en el servidor como cantidad × valor.
 */
public record IngresoRequest(
        @NotNull @Min(0) @Max(1000000) Integer cantidad) {}
