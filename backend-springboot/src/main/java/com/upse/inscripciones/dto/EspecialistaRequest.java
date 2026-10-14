package com.upse.inscripciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO de entrada para crear/actualizar un Especialista (POST/PUT
 * application/x-www-form-urlencoded). telefono/correo llegan siempre como
 * "" (no ausentes) cuando el campo queda vacío en el formulario, por eso
 * los patrones incluyen la alternativa "^$" para no exigir el formato en
 * un campo opcional vacío — mismo comportamiento que la validación manual
 * que reemplaza.
 * <p>
 * Los límites de @Size replican el largo de columna definido en
 * {@link com.upse.inscripciones.entity.Especialista}.
 */
@Data
public class EspecialistaRequest {

    // Cédula ecuatoriana (10 dígitos) o número de pasaporte (alfanumérico,
    // 5 a 20 caracteres) — debe reflejar el mismo criterio validado en
    // EspecialistaService y el largo de columna real (varchar(20)).
    @NotBlank(message = "El documento de identidad es obligatorio.")
    @Pattern(regexp = "^\\d{10}$|^[A-Za-z0-9]{5,20}$",
            message = "El documento de identidad debe ser una cédula de 10 dígitos o un número de pasaporte válido (5 a 20 caracteres).")
    private String cedula;

    @NotBlank(message = "Los nombres son obligatorios.")
    @Size(max = 80, message = "Los nombres no pueden superar los 80 caracteres.")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios.")
    @Size(max = 80, message = "Los apellidos no pueden superar los 80 caracteres.")
    private String apellidos;

    // Formato libre: la normalización a E.164 (+593...) la hace
    // TelefonoUtils.normalizar() en el service. Acá solo se limita el
    // largo (coincide con la columna varchar(20)) y caracteres permitidos.
    @Pattern(regexp = "^$|^(?:\\+|00)?[\\d\\s()\\-]{7,20}$",
            message = "Ingrese un Número de WhatsApp válido, incluyendo el código de país.")
    @Size(max = 20, message = "El Número de WhatsApp no puede superar los 20 caracteres.")
    private String telefono;

    @Pattern(regexp = "^$|^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$", message = "El correo electrónico no tiene un formato válido.")
    @Size(max = 100, message = "El correo no puede superar los 100 caracteres.")
    private String correo;

    @Size(max = 100, message = "La especialidad no puede superar los 100 caracteres.")
    private String especialidad;

    @Size(max = 150, message = "El área de conocimiento no puede superar los 150 caracteres.")
    private String areaConocimiento;

    // Código ISO del país (ej. "EC", "CO"). Opcional; se valida contra el
    // catálogo de Nacionalidades en el service, no aquí, porque la lista
    // de códigos válidos vive en un solo lugar (Nacionalidades.java).
    @Size(max = 5, message = "El código de país no es válido.")
    private String paisNacionalidad;
}
