package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CoordinadorResponse;
import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.util.TelefonoUtils;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CoordinadorService {

    private final CoordinadorRepository coordinadorRepository;
    private final com.upse.inscripciones.repository.PlanificacionRepository planificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final FileStorageService fileStorageService;

    private static final Pattern PASAPORTE_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern CEDULA_PATTERN  = Pattern.compile("^\\d{10}$");
    private static final Pattern EMAIL_PATTERN    =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    // ── Listar ────────────────────────────────────────────────────────────────

    public List<CoordinadorResponse> listarTodos() {
        return coordinadorRepository.findAll()
                .stream()
                .map(CoordinadorResponse::fromEntity)
                .toList();
    }

    // Listado paginado para coordinadores.html, con búsqueda opcional y
    // filtro de estado (mismo patrón con Specification que CursoService).
    public Page<CoordinadorResponse> listarPaginado(String texto, Boolean estado, Pageable pageable) {
        return coordinadorRepository.findAll(construirSpecification(texto, estado), pageable)
                .map(CoordinadorResponse::fromEntity);
    }

    private Specification<Coordinador> construirSpecification(String texto, Boolean estado) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();

        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (textoNormalizado != null) {
                String patron = "%" + textoNormalizado + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombres")), patron),
                        cb.like(cb.lower(root.get("apellidos")), patron),
                        cb.like(cb.lower(root.get("cedula")), patron),
                        cb.like(cb.lower(root.get("correo")), patron)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    // ✅ NUEVO: para el botón "Exportar a Excel" de coordinadores.html.
    public List<CoordinadorResponse> listarParaExportar(String texto, Boolean estado) {
        return coordinadorRepository.findAll(
                        construirSpecification(texto, estado),
                        org.springframework.data.domain.Sort.by("id").descending())
                .stream()
                .map(CoordinadorResponse::fromEntity)
                .toList();
    }

    public List<CoordinadorResponse> listarActivos() {
        return coordinadorRepository.findByEstadoTrue()
                .stream()
                .map(CoordinadorResponse::fromEntity)
                .toList();
    }

    public CoordinadorResponse obtenerPorId(Long id) {
        return CoordinadorResponse.fromEntity(buscarPorId(id));
    }

    // ── Registrar ─────────────────────────────────────────────────────────────

    @Transactional
    public CoordinadorResponse registrar(String cedula, String nombres, String apellidos,
                                         String telefono, String correo,
                                         MultipartFile foto) {
        validar(cedula, nombres, apellidos, telefono, correo);

        if (coordinadorRepository.existsByCedula(cedula.trim())) {
            throw new ValidacionException("Ya existe un coordinador con la cédula " + cedula + ".");
        }
        String correoNormalizado = normalizarCorreo(correo);
        if (correoNormalizado != null
                && coordinadorRepository.existsByCorreo(correoNormalizado)) {
            throw new ValidacionException("Ya existe un coordinador con el correo " + correo + ".");
        }

        String rutaFoto = null;
        if (foto != null && !foto.isEmpty()) {
            rutaFoto = fileStorageService.guardarArchivoOpcional(
                    foto, "fotos-coordinadores", "Foto", cedula.trim(), nombres.trim() + " " + apellidos.trim());
        }

        Coordinador coordinador = Coordinador.builder()
                .cedula(cedula.trim())
                .nombres(nombres.trim())
                .apellidos(apellidos.trim())
                .telefono(telefono == null || telefono.isBlank() ? null : TelefonoUtils.normalizar(telefono))
                .correo(correoNormalizado)
                .foto(rutaFoto)
                .build();

        return CoordinadorResponse.fromEntity(coordinadorRepository.save(coordinador));
    }

    // ── Actualizar ────────────────────────────────────────────────────────────

    @Transactional
    public CoordinadorResponse actualizar(Long id, String cedula, String nombres, String apellidos,
                                          String telefono, String correo,
                                          MultipartFile foto) {
        Coordinador coordinador = buscarPorId(id);
        validar(cedula, nombres, apellidos, telefono, correo);

        if (!coordinador.getCedula().equals(cedula.trim())
                && coordinadorRepository.existsByCedula(cedula.trim())) {
            throw new ValidacionException("Ya existe otro coordinador con la cédula " + cedula + ".");
        }
        String correoNormalizado = normalizarCorreo(correo);
        if (correoNormalizado != null
                && (coordinador.getCorreo() == null || !coordinador.getCorreo().equals(correoNormalizado))
                && coordinadorRepository.existsByCorreo(correoNormalizado)) {
            throw new ValidacionException("Ya existe otro coordinador con el correo " + correo + ".");
        }

        coordinador.setCedula(cedula.trim());
        coordinador.setNombres(nombres.trim());
        coordinador.setApellidos(apellidos.trim());
        coordinador.setTelefono(telefono == null || telefono.isBlank() ? null : TelefonoUtils.normalizar(telefono));
        coordinador.setCorreo(correoNormalizado);
        String rutaFoto = null;
        if (foto != null && !foto.isEmpty()) {
            rutaFoto = fileStorageService.guardarArchivoOpcional(
                    foto, "fotos-coordinadores", "Foto", cedula.trim(), nombres.trim() + " " + apellidos.trim());
        }
        if (rutaFoto != null) {
            coordinador.setFoto(rutaFoto);
        }

        return CoordinadorResponse.fromEntity(coordinadorRepository.save(coordinador));
    }

    // ── Cambiar estado ────────────────────────────────────────────────────────

    @Transactional
    public CoordinadorResponse cambiarEstado(Long id, boolean estado) {
        Coordinador coordinador = buscarPorId(id);
        coordinador.setEstado(estado);
        return CoordinadorResponse.fromEntity(coordinadorRepository.save(coordinador));
    }

    @Transactional
    public void eliminar(Long id) {
        Coordinador coordinador = buscarPorId(id);
        // Mismo caso que en CursoService#eliminar: hay una FK real hacia
        // Planificacion (id_coordinador NOT NULL) sin ON DELETE CASCADE.
        if (planificacionRepository.existsByCoordinadorId(id)) {
            throw new ValidacionException(
                    "No se puede eliminar al coordinador \"" + coordinador.getNombres() + " " +
                            coordinador.getApellidos() + "\": hay una planificación activa asignada a él. " +
                            "Elimina o reasigna primero esa planificación.");
        }
        if (usuarioRepository.findByCoordinadorId(id).isPresent()) {
            throw new ValidacionException(
                    "No se puede eliminar al coordinador \"" + coordinador.getNombres() + " "
                            + coordinador.getApellidos() + "\": tiene una cuenta de usuario asociada. "
                            + "Elimina o reasigna primero esa cuenta desde Gestión de Usuarios.");
        }
        coordinadorRepository.deleteById(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Coordinador buscarPorId(Long id) {
        return coordinadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el coordinador con id " + id + "."));
    }

    private void validar(String cedula, String nombres, String apellidos,
                         String telefono, String correo) {
        if (cedula == null
                || !(CEDULA_PATTERN.matcher(cedula.trim()).matches()
                     || PASAPORTE_PATTERN.matcher(cedula.trim()).matches())) {
            throw new ValidacionException("El documento de identidad debe ser una cédula de 10 dígitos o un número de pasaporte válido (5 a 20 caracteres).");
        }
        if (nombres == null || nombres.trim().isEmpty()) {
            throw new ValidacionException("Los nombres son obligatorios.");
        }
        if (apellidos == null || apellidos.trim().isEmpty()) {
            throw new ValidacionException("Los apellidos son obligatorios.");
        }
        // La normalización/validación de formato internacional del teléfono
        // (opcional) la hace TelefonoUtils.normalizar() más abajo, al guardar.
        if (correo != null && !correo.isBlank()
                && !EMAIL_PATTERN.matcher(correo.trim()).matches()) {
            throw new ValidacionException("El correo electrónico no tiene un formato válido.");
        }
    }

    private String normalizarCorreo(String correo) {
        return correo == null || correo.isBlank() ? null : correo.trim().toLowerCase();
    }
}
