package com.upse.inscripciones.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * DTO de entrada para crear/actualizar un Curso (POST/PUT multipart).
 * <p>
 * modalidad y estado se mantienen como String (no como el enum
 * directamente) porque el binding automático de @ModelAttribute a enums es
 * sensible a mayúsculas/espacios, y el frontend no siempre manda el valor
 * exacto del enum (ej. "Presencial" en vez de "PRESENCIAL", o "En Espera"
 * en vez de "EN_ESPERA"). El controller sigue normalizando y traduciendo
 * estos dos campos con los mismos mensajes de error en español que ya
 * existían, después de que Bean Validation confirma que el resto de
 * campos básicos son válidos.
 * <p>
 * Los límites de @Size replican el largo de columna definido en
 * {@link com.upse.inscripciones.entity.Curso}.
 */
@Data
public class CursoRequest {

    @NotBlank(message = "El nombre del curso es obligatorio.")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres.")
    private String nombre;

    @NotBlank(message = "El código del curso es obligatorio.")
    @Size(max = 20, message = "El código no puede superar los 20 caracteres.")
    private String codigo;

    @NotNull(message = "Las horas son obligatorias.")
    @Min(value = 1, message = "Las horas deben ser un número mayor a 0.")
    private Integer horas;

    @NotNull(message = "El costo es obligatorio.")
    @DecimalMin(value = "0.0", message = "El costo debe ser un valor mayor o igual a 0.")
    @Digits(integer = 8, fraction = 2, message = "El costo admite hasta 8 cifras enteras y 2 decimales.")
    private BigDecimal costo;

    @NotNull(message = "Los cupos totales son obligatorios.")
    @Min(value = 1, message = "Los cupos totales deben ser un número mayor a 0.")
    private Integer cuposTotales;

    // Opcional: si no se manda, el service lo iguala a cuposTotales.
    @PositiveOrZero(message = "Los cupos restantes no pueden ser negativos.")
    private Integer cuposRestantes;

    @NotBlank(message = "La modalidad es obligatoria.")
    @Size(max = 20, message = "La modalidad no es válida.")
    private String modalidad;

    // Opcional: solo se usa al crear/editar si se quiere fijar un estado
    // explícito; si no se manda, el service usa el valor por defecto.
    @Size(max = 20, message = "El estado no es válido.")
    private String estado;

    private MultipartFile foto;

    private com.upse.inscripciones.entity.AmbitoCurso ambito;
}
