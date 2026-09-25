package com.upse.inscripciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO de entrada para crear/actualizar un Coordinador (POST/PUT multipart).
 * Mismas reglas que EspecialistaRequest (cédula/teléfono/correo comparten
 * formato en toda la aplicación); telefono/correo permiten vacío porque el
 * formulario los manda siempre presentes aunque el usuario no los llene.
 * <p>
 * Los límites de @Size replican exactamente el largo de columna definido en
 * la entidad {@link com.upse.inscripciones.entity.Coordinador} (que a su vez
 * refleja el varchar real en Postgres). Sin esto, un valor más largo que la
 * columna llega hasta Hibernate y falla con una excepción genérica de
 * "conflicto con datos existentes" en vez de un mensaje claro de validación.
 */
@Data
public class CoordinadorRequest {

    // Cédula ecuatoriana (10 dígitos) o número de pasaporte (alfanumérico,
    // 5 a 20 caracteres) — debe reflejar el mismo criterio validado en
    // CoordinadorService y el largo de columna real (varchar(20)).
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

    private MultipartFile foto;
}
