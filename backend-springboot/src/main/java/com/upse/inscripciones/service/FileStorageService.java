package com.upse.inscripciones.service;

import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FileStorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    // Extensiones permitidas para los documentos del formulario.
    // heic/heif se incluyen porque es el formato real que graba un iPhone
    // por defecto: si el usuario sube la foto "tal cual" desde el celular
    // (a veces con nombre "foto.jpg" pero contenido HEIC), antes la
    // validación de contenido (ver MIME_ESPERADO_POR_EXTENSION) la
    // rechazaba igual porque el magic-byte real no coincidía con JPEG.
    private static final List<String> EXTENSIONES_PERMITIDAS =
            List.of("jpg", "jpeg", "png", "pdf", "heic", "heif");

    private static final long TAMANIO_MAXIMO_BYTES = 5L * 1024 * 1024; // 5MB

    // Detecta el tipo de archivo real a partir de su contenido (magic bytes),
    // no de la extensión declarada por el cliente. Tika es thread-safe y
    // liviano, así que una sola instancia compartida es suficiente.
    private final Tika tika = new Tika();

    // MIME real esperado según la extensión declarada. Si Tika detecta algo
    // fuera de esta lista, el archivo no es lo que dice ser y se rechaza.
    private static final Map<String, List<String>> MIME_ESPERADO_POR_EXTENSION = Map.of(
            "jpg", List.of("image/jpeg"),
            "jpeg", List.of("image/jpeg"),
            "png", List.of("image/png"),
            "pdf", List.of("application/pdf"),
            "heic", List.of("image/heic", "image/heic-sequence"),
            "heif", List.of("image/heif", "image/heif-sequence")
    );

    // MIMEs reales de foto de celular (iPhone) que se aceptan aunque el
    // nombre del archivo declare .jpg/.jpeg/.png: cuando alguien comparte
    // la foto "tal cual" (sin que la app de origen la convierta), el
    // sistema operativo a veces conserva un nombre "foto.jpg" pero el
    // contenido real sigue siendo HEIC/HEIF. En ese caso no se rechaza el
    // archivo, se guarda con la extensión que de verdad le corresponde.
    private static final List<String> MIME_HEIC_HEIF =
            List.of("image/heic", "image/heic-sequence", "image/heif", "image/heif-sequence");

    /**
     * Guarda un archivo en disco dentro de una subcarpeta (por tipo de documento).
     * Conserva cédula y nombre en el prefijo y añade un sufijo único.
     * Una persona puede inscribirse en varias planificaciones: su documento
     * nuevo nunca debe sobrescribir ni eliminar el de una inscripción previa.
     */
    public String guardarArchivo(MultipartFile file, String subcarpeta, String campo,
                                  String cedula, String nombreCompleto) {
        if (file == null || file.isEmpty()) {
            throw new ValidacionException("El archivo '" + campo + "' es obligatorio.");
        }
        return guardarInterno(file, subcarpeta, campo, cedula, nombreCompleto);
    }

    /**
     * Elimina un archivo previamente guardado con guardarArchivo/guardarArchivoOpcional.
     * Se usa para limpiar archivos huérfanos cuando un paso POSTERIOR del
     * flujo falla (ej. en InscripcionService#registrarInscripcion: si el
     * tercer documento falla su validación, los dos ya guardados en disco
     * quedarían huérfanos porque el @Transactional solo revierte la base
     * de datos, no el filesystem). No lanza excepción si falla el borrado
     * (ya estamos en medio de un manejo de error; no queremos ocultar el
     * error original con uno nuevo) — solo lo registra en el log.
     *
     * @param rutaRelativa la ruta devuelta por guardarArchivo (subcarpeta/nombreArchivo), o null (no hace nada)
     */
    public void eliminarSiExiste(String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isBlank()) {
            return;
        }
        try {
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path ruta = base.resolve(rutaRelativa).normalize();
            if (!ruta.startsWith(base) || ruta.equals(base)) {
                log.warn("Se rechazó una ruta de limpieza fuera del directorio de archivos.");
                return;
            }
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            log.warn("No se pudo eliminar el archivo huérfano '{}': {}", rutaRelativa, e.getMessage());
        }
    }

    /** Igual que guardarArchivo, pero si no viene ningún archivo simplemente retorna null (no lanza error). */
    public String guardarArchivoOpcional(MultipartFile file, String subcarpeta, String campo,
                                          String cedula, String nombreCompleto) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return guardarInterno(file, subcarpeta, campo, cedula, nombreCompleto);
    }

    /** Guarda bytes ya generados por el sistema (ej. un PDF) bajo un nombre de archivo dado. */
    public String guardarBytes(byte[] contenido, String subcarpeta, String nombreArchivo) {
        try {
            return escribirArchivoUnico(contenido, subcarpeta, nombreArchivo);
        } catch (IOException e) {
            throw new ValidacionException("No se pudo almacenar el archivo generado: " + e.getMessage());
        }
    }

    private String guardarInterno(MultipartFile file, String subcarpeta, String campo,
                                   String cedula, String nombreCompleto) {
        if (file.getSize() > TAMANIO_MAXIMO_BYTES) {
            throw new ValidacionException("El archivo '" + campo + "' supera el tamaño máximo de 5MB.");
        }

        String nombreOriginal = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = obtenerExtension(nombreOriginal).toLowerCase();

        if (!EXTENSIONES_PERMITIDAS.contains(extension)) {
            throw new ValidacionException(
                    "El archivo '" + campo + "' debe ser JPG, PNG, HEIC o PDF.");
        }

        byte[] contenido;
        try {
            contenido = file.getBytes();
        } catch (IOException e) {
            throw new ValidacionException(
                    "No se pudo leer el archivo '" + campo + "': " + e.getMessage());
        }

        // La extensión solo dice lo que el cliente AFIRMA que es el archivo.
        // Se valida el contenido real (magic bytes) para evitar que alguien
        // suba, por ejemplo, un ejecutable renombrado como "comprobante.pdf".
        String mimeReal = tika.detect(contenido);
        List<String> mimesPermitidos = MIME_ESPERADO_POR_EXTENSION.get(extension);
        boolean coincide = mimesPermitidos != null && mimesPermitidos.contains(mimeReal);

        if (!coincide && esExtensionDeImagen(extension) && MIME_HEIC_HEIF.contains(mimeReal)) {
            // Foto de iPhone sin convertir: el nombre dice .jpg/.jpeg/.png
            // pero el contenido real es HEIC/HEIF. Se acepta igual y se
            // corrige la extensión para que coincida con el contenido real
            // (si no, quedaría un archivo ".jpg" en disco que en realidad
            // es HEIC y ningún visor lo podría abrir).
            extension = mimeReal.startsWith("image/heic") ? "heic" : "heif";
            coincide = true;
        }

        if (!coincide) {
            throw new ValidacionException(
                    "El archivo '" + campo + "' no es un " + extension.toUpperCase()
                            + " válido (el contenido no coincide con la extensión).");
        }

        try {
            String nombreArchivo = construirNombreArchivo(cedula, nombreCompleto, extension);
            return escribirArchivoUnico(contenido, subcarpeta, nombreArchivo);
        } catch (IOException e) {
            throw new ValidacionException(
                    "No se pudo almacenar el archivo '" + campo + "': " + e.getMessage());
        }
    }

    private String escribirArchivoUnico(byte[] contenido, String subcarpeta, String nombreArchivo) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path carpeta = base.resolve(subcarpeta).normalize();
        if (!carpeta.startsWith(base) || carpeta.equals(base) || nombreArchivo.contains("..")
                || nombreArchivo.contains("/") || nombreArchivo.contains("\\")) {
            throw new ValidacionException("La ruta del archivo no es válida.");
        }
        Files.createDirectories(carpeta);
        int punto = nombreArchivo.lastIndexOf('.');
        String prefijo = punto > 0 ? nombreArchivo.substring(0, punto) : nombreArchivo;
        String extension = punto > 0 ? nombreArchivo.substring(punto) : "";
        Path destino = Files.createTempFile(carpeta, prefijo + "_", extension);
        try {
            Files.write(destino, contenido);
        } catch (IOException error) {
            Files.deleteIfExists(destino);
            throw error;
        }
        String relativa = subcarpeta + "/" + destino.getFileName();
        // Incluye fallos del commit que se producen después de retornar del service.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) eliminarSiExiste(relativa);
                }
            });
        }
        return relativa;
    }

    /**
     * Resuelve de forma segura la ruta física de un archivo servido por los
     * endpoints "/archivos/{subcarpeta}/{nombreArchivo}".
     * <p>
     * subcarpeta y nombreArchivo vienen directo del cliente (son parte de la
     * URL), así que NUNCA deben usarse para construir una ruta sin validar:
     * 1) la subcarpeta debe estar en la lista blanca del endpoint que llama
     *    (cada controller solo permite las subcarpetas que le corresponden,
     *    p. ej. cursos solo sirve "fotos-cursos", nunca "copias-cedula");
     * 2) el nombre de archivo no puede contener separadores de ruta ni "..";
     * 3) la ruta final, ya resuelta, debe seguir estando dentro de
     *    uploadDir (protección extra ante cualquier variante de traversal).
     * <p>
     * Ante cualquier violación se lanza RecursoNoEncontradoException (no
     * ValidacionException) para que la respuesta sea siempre un 404 genérico
     * y no revele si el problema fue la subcarpeta, el nombre o la ruta.
     */
    public Path resolverRutaSegura(String subcarpeta, String nombreArchivo, List<String> subcarpetasPermitidas) {
        if (subcarpeta == null || !subcarpetasPermitidas.contains(subcarpeta)) {
            throw new RecursoNoEncontradoException("Archivo no encontrado.");
        }
        if (nombreArchivo == null || nombreArchivo.isBlank()
                || nombreArchivo.contains("..")
                || nombreArchivo.contains("/")
                || nombreArchivo.contains("\\")) {
            throw new RecursoNoEncontradoException("Archivo no encontrado.");
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path destino = base.resolve(subcarpeta).resolve(nombreArchivo).normalize();

        if (!destino.startsWith(base)) {
            throw new RecursoNoEncontradoException("Archivo no encontrado.");
        }

        return destino;
    }

    /**
     * Sirve un archivo previamente subido como respuesta HTTP lista para
     * usar (200 con el Content-Type correcto + Content-Disposition inline,
     * o el error apropiado si no existe). Centraliza lo que antes estaba
     * copiado casi igual en CursoController, CoordinadorController e
     * InscripcionController — cada uno solo declara su propia lista blanca
     * de subcarpetas y delega aquí.
     */
    public ResponseEntity<Resource> servirArchivo(String subcarpeta, String nombreArchivo,
                                                   List<String> subcarpetasPermitidas) {
        try {
            Path ruta = resolverRutaSegura(subcarpeta, nombreArchivo, subcarpetasPermitidas);
            Resource recurso = new UrlResource(ruta.toUri());

            if (!recurso.exists() || !recurso.isReadable()) {
                throw new RecursoNoEncontradoException("Archivo no encontrado: " + nombreArchivo);
            }

            return ResponseEntity.ok()
                    .contentType(resolverContentType(nombreArchivo))
                    .header("Content-Disposition", "inline; filename=\"" + recurso.getFilename() + "\"")
                    .body(recurso);
        } catch (MalformedURLException e) {
            throw new RecursoNoEncontradoException("Archivo no encontrado: " + nombreArchivo);
        }
    }

    /**
     * Determina el Content-Type según la extensión, para que el navegador
     * muestre el archivo inline (imagen o PDF) en vez de descargarlo como
     * binario genérico.
     */
    private MediaType resolverContentType(String nombreArchivo) {
        String nombre = nombreArchivo.toLowerCase();
        if (nombre.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (nombre.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (nombre.endsWith(".heic")) {
            return MediaType.parseMediaType("image/heic");
        }
        if (nombre.endsWith(".heif")) {
            return MediaType.parseMediaType("image/heif");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String construirNombreArchivo(String cedula, String nombreCompleto, String extension) {
        String cedulaLimpia = (cedula == null || cedula.isBlank()) ? "sin_cedula" : cedula.trim();
        String nombreLimpio = sanitizarNombre(nombreCompleto);
        return cedulaLimpia + "_" + nombreLimpio + "." + extension;
    }

    /**
     * Convierte el nombre completo en un texto seguro para nombre de archivo:
     * quita tildes/ñ, pasa a minúsculas, reemplaza espacios por guiones bajos
     * y elimina cualquier caracter que no sea letra, número o guion bajo.
     * Ej: "Charles Pérez" -> "charles_perez"
     */
    private String sanitizarNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "sin_nombre";
        }

        String sinTildes = Normalizer.normalize(nombreCompleto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // quita los acentos/diacríticos

        String limpio = sinTildes.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "") // quita cualquier símbolo raro
                .trim()
                .replaceAll("\\s+", "_");       // espacios -> guion bajo

        if (limpio.isBlank()) {
            return "sin_nombre";
        }

        // Evita nombres de archivo excesivamente largos
        return limpio.length() > 60 ? limpio.substring(0, 60) : limpio;
    }

    private boolean esExtensionDeImagen(String extension) {
        return "jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension);
    }

    private String obtenerExtension(String nombreArchivo) {
        int i = nombreArchivo.lastIndexOf('.');
        if (i == -1 || i == nombreArchivo.length() - 1) {
            throw new ValidacionException("El archivo no tiene una extensión válida.");
        }
        return nombreArchivo.substring(i + 1);
    }
}
