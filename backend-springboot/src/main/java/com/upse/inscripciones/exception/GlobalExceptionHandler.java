package com.upse.inscripciones.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> manejarEdicionConcurrente(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("exito", false,
                "mensaje", "Otra persona modificó estos datos. Actualiza la pantalla antes de guardar."));
    }

    /**
     * Maneja RecursoNoEncontradoException
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<?> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex,
            WebRequest request) {

        log.warn("Recurso no encontrado: {}", ex.getMessage());

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", ex.getMessage());
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(respuesta);
    }

    /**
     * Maneja ValidacionException
     */
    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<?> manejarValidacion(
            ValidacionException ex,
            WebRequest request) {

        log.warn("Error de validación: {}", ex.getMessage());

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", ex.getMessage());
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    /**
     * Maneja errores de validación de @Valid
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> manejarValidacionArgumento(
            BindException ex,
            WebRequest request) {

        log.warn("Error en validación de argumentos");

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", "Error en los datos proporcionados");
        
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errores.put(error.getField(), error.getDefaultMessage())
        );
        
        respuesta.put("errores", errores);
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(respuesta);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> manejarParametrosInvalidos(Exception ex) {
        String mensaje = "Los datos enviados no tienen un formato válido.";
        if (ex instanceof MethodArgumentTypeMismatchException error) {
            mensaje = "El parámetro '" + error.getName() + "' no tiene un valor válido.";
        } else if (ex instanceof MissingServletRequestParameterException error) {
            mensaje = "Falta el campo obligatorio '" + error.getParameterName() + "'.";
        } else if (ex instanceof MissingServletRequestPartException error) {
            mensaje = "Falta el archivo obligatorio '" + error.getRequestPartName() + "'.";
        }
        return ResponseEntity.badRequest().body(Map.of("exito", false, "mensaje", mensaje, "timestamp", LocalDateTime.now()));
    }

    /**
     * Maneja archivos que superan spring.servlet.multipart.max-file-size
     * (o max-request-size). Spring Boot corta la petición en el filtro de
     * multipart ANTES de que llegue a FileStorageService, así que sin este
     * handler el usuario ve el 500 genérico de "Ocurrió un error interno
     * del servidor" en vez del mensaje claro de "supera el tamaño máximo".
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> manejarArchivoDemasiadoGrande(
            MaxUploadSizeExceededException ex,
            WebRequest request) {

        log.warn("Archivo(s) superan el tamaño máximo permitido: {}", ex.getMessage());

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", "El archivo supera el tamaño máximo permitido de 5MB.");
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(respuesta);
    }

    /**
     * Maneja violaciones de restricciones de la base de datos (ej. una
     * restricción UNIQUE, como la cédula de una inscripción). Es el
     * respaldo para el caso raro en que dos peticiones casi simultáneas
     * pasen ambas la validación de "existsBy..." en la aplicación antes de
     * que cualquiera confirme su escritura; sin este handler, el usuario
     * vería un 500 genérico con detalles internos de la base de datos en
     * vez de un mensaje claro.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> manejarViolacionIntegridad(
            DataIntegrityViolationException ex,
            WebRequest request) {

        log.warn("Violación de restricción de base de datos: {}", ex.getMessage());

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", "El registro no pudo guardarse porque entra en conflicto con datos existentes " +
                "(por ejemplo, un valor que ya está registrado). Verifica los datos e intenta de nuevo.");
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta);
    }

    /**
     * Maneja recursos estáticos no encontrados (ej. favicon.ico, o
     * peticiones que hace el propio navegador como
     * .well-known/appspecific/com.chrome.devtools.json). Sin este handler,
     * caían en el genérico de abajo y se registraban como "Error inesperado"
     * a nivel ERROR, ensuciando el log real con ruido inofensivo del
     * navegador que no tiene nada que ver con la aplicación.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> manejarRecursoEstaticoNoEncontrado(
            org.springframework.web.servlet.resource.NoResourceFoundException ex,
            WebRequest request) {

        log.debug("Recurso estático no encontrado: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * Las excepciones de autorización de @PreAuthorize se producen dentro
     * de Spring MVC (después de que el filtro de seguridad ya pasó), por lo
     * que llegan a este @RestControllerAdvice. Deben conservar el significado
     * HTTP 403 en vez de caer en el handler genérico 500.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> manejarAccesoDenegado(
            org.springframework.security.access.AccessDeniedException ex,
            WebRequest request) {

        log.warn("Acceso denegado: {}", ex.getMessage());

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", "Acceso denegado");
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(respuesta);
    }

    /**
     * Maneja cualquier otra excepción
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> manejarExcepcionGeneral(
            Exception ex,
            WebRequest request) {

        log.error("Error inesperado: ", ex);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("exito", false);
        respuesta.put("mensaje", "Ocurrió un error interno del servidor");
        respuesta.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta);
    }
}
