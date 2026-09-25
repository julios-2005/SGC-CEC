package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CursoResponse;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.CursoRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CursoService {

    private final CursoRepository cursoRepository;
    private final FileStorageService fileStorageService;
    private final InformeEconomicoService informeEconomicoService;
    private final com.upse.inscripciones.repository.PlanificacionRepository planificacionRepository;

    @Transactional
    public CursoResponse guardarFormulario(Long id, com.upse.inscripciones.dto.CursoRequest r,
                                           Modalidad modalidad, EstadoCurso estado) {
        CursoResponse resultado = id == null
                ? registrar(r.getNombre(), r.getCodigo(), r.getHoras(), r.getCosto(),
                    r.getCuposTotales(), r.getCuposRestantes(), modalidad, estado, r.getFoto())
                : actualizar(id, r.getNombre(), r.getCodigo(), r.getHoras(), r.getCosto(),
                    r.getCuposTotales(), r.getCuposRestantes(), modalidad, estado, r.getFoto());
        Curso curso = buscarPorId(resultado.getIdCurso());
        if (r.getAmbito() != null) curso.setAmbito(r.getAmbito());
        else if (id == null) curso.setAmbito(com.upse.inscripciones.entity.AmbitoCurso.NACIONAL);
        return CursoResponse.fromEntity(cursoRepository.save(curso));
    }

    // ── Listar ────────────────────────────────────────────────────────────────

    public List<CursoResponse> listarTodos() {
        return cursoRepository.findAll()
                .stream()
                .map(CursoResponse::fromEntity)
                .toList();
    }

    /**
     * Listado paginado para CursoController#listarTodos. "texto" filtra por
     * nombre o código (se normaliza acá: recortado y en minúsculas, o null
     * si viene vacío). "estado" filtra por estado exacto, o null para
     * "todos los estados".
     * <p>
     * Se arma con Specification (Criteria API) en vez del antiguo patrón
     * "@Query con (:param IS NULL OR campo = :param)": ese patrón, aunque
     * es JPQL válido, dispara un bug real de esta versión de Hibernate al
     * combinarse con Pageable — el bind de un parámetro null en la
     * comparación de igualdad (sobre todo con un enum) rompe la derivación
     * de la query de COUNT necesaria para paginar, y termina en un 500 en
     * cuanto se listan cursos sin pasar ambos filtros. Con Specification,
     * cada condición opcional simplemente no se agrega al Predicate cuando
     * el filtro es null, así que nunca se bindea un parámetro null contra
     * el campo — se evita el problema de raíz en vez de rodearlo.
     */
    public Page<CursoResponse> listarTodos(String texto, EstadoCurso estado, Modalidad modalidad, Pageable pageable) {
        return cursoRepository.findAll(construirSpecification(texto, estado, modalidad), pageable)
                .map(CursoResponse::fromEntity);
    }

    private Specification<Curso> construirSpecification(String texto, EstadoCurso estado, Modalidad modalidad) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();

        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (modalidad != null) {
                condiciones.add(cb.equal(root.get("modalidad"), modalidad));
            }
            if (textoNormalizado != null) {
                String patron = "%" + textoNormalizado + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), patron),
                        cb.like(cb.lower(root.get("codigo")), patron)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    // ✅ NUEVO: para el botón "Exportar a Excel" de cursos.html. Mismos
    // filtros que listarTodos(...), pero sin paginar (todas las filas que
    // coincidan) y ordenado por nombre.
    public List<CursoResponse> listarParaExportar(String texto, EstadoCurso estado, Modalidad modalidad) {
        return cursoRepository.findAll(
                        construirSpecification(texto, estado, modalidad),
                        org.springframework.data.domain.Sort.by("fechaRegistro").descending())
                .stream()
                .map(CursoResponse::fromEntity)
                .toList();
    }

    public List<CursoResponse> listarPorEstado(EstadoCurso estado) {
        return cursoRepository.findByEstado(estado)
                .stream()
                .map(CursoResponse::fromEntity)
                .toList();
    }

    public CursoResponse obtenerPorId(Long id) {
        return CursoResponse.fromEntity(buscarPorId(id));
    }

    // ── Registrar ─────────────────────────────────────────────────────────────

    @Transactional
    public CursoResponse registrar(String nombre, String codigo, Integer horas, BigDecimal costo,
                                    Integer cuposTotales, Integer cuposRestantes,
                                    Modalidad modalidad, EstadoCurso estado, MultipartFile foto) {
        validar(nombre, codigo, horas, costo, cuposTotales, cuposRestantes, modalidad);

        if (cursoRepository.existsByNombreIgnoreCase(nombre.trim())) {
            throw new ValidacionException("Ya existe un curso registrado con el nombre \"" + nombre + "\".");
        }
        if (cursoRepository.existsByCodigoIgnoreCase(codigo.trim())) {
            throw new ValidacionException("Ya existe un curso registrado con el código \"" + codigo + "\".");
        }

        String rutaFoto = fileStorageService.guardarArchivoOpcional(
                foto, "fotos-cursos", "Foto", "curso", nombre.trim());

        Curso curso = Curso.builder()
                .nombre(nombre.trim())
                .codigo(codigo.trim())
                .horas(horas)
                .costo(costo)
                .cuposTotales(cuposTotales)
                .cuposRestantes(cuposRestantes != null ? cuposRestantes : cuposTotales)
                .modalidad(modalidad)
                .estado(estado != null ? estado : EstadoCurso.EN_ESPERA)
                .foto(rutaFoto)
                .build();

        return CursoResponse.fromEntity(cursoRepository.save(curso));
    }

    // ── Actualizar ────────────────────────────────────────────────────────────

    @Transactional
    public CursoResponse actualizar(Long id, String nombre, String codigo, Integer horas, BigDecimal costo,
                                     Integer cuposTotales, Integer cuposRestantes,
                                     Modalidad modalidad, EstadoCurso estado, MultipartFile foto) {
        Curso curso = buscarPorId(id);
        validar(nombre, codigo, horas, costo, cuposTotales, cuposRestantes, modalidad);

        if (!curso.getNombre().equalsIgnoreCase(nombre.trim())
                && cursoRepository.existsByNombreIgnoreCase(nombre.trim())) {
            throw new ValidacionException("Ya existe otro curso con el nombre \"" + nombre + "\".");
        }
        if (!curso.getCodigo().equalsIgnoreCase(codigo.trim())
                && cursoRepository.existsByCodigoIgnoreCase(codigo.trim())) {
            throw new ValidacionException("Ya existe otro curso con el código \"" + codigo + "\".");
        }

        curso.setNombre(nombre.trim());
        curso.setCodigo(codigo.trim());
        curso.setHoras(horas);
        curso.setCosto(costo);
        int ocupados = curso.getCuposTotales() - curso.getCuposRestantes();
        if (cuposTotales < ocupados) {
            throw new ValidacionException("Los cupos totales no pueden ser menores que los cupos ya ocupados.");
        }
        curso.setCuposTotales(cuposTotales);
        curso.setCuposRestantes(cuposRestantes != null ? cuposRestantes : cuposTotales - ocupados);
        curso.setModalidad(modalidad);
        if (estado != null) {
            curso.setEstado(estado);
        }
        String rutaFoto = fileStorageService.guardarArchivoOpcional(
                foto, "fotos-cursos", "Foto", "curso", nombre.trim());
        if (rutaFoto != null) {
            curso.setFoto(rutaFoto);
        }

        Curso guardado = cursoRepository.save(curso);

        // El costo del curso alimenta todas sus planificaciones: hay que
        // recalcular el Informe Económico de cada una.
        informeEconomicoService.recalcularPorCurso(guardado.getIdCurso());

        return CursoResponse.fromEntity(guardado);
    }

    // ── Cambiar estado ────────────────────────────────────────────────────────

    @Transactional
    public CursoResponse cambiarEstado(Long id, EstadoCurso estado) {
        Curso curso = buscarPorId(id);
        if (estado == null) {
            throw new ValidacionException("El estado es obligatorio.");
        }
        curso.setEstado(estado);
        return CursoResponse.fromEntity(cursoRepository.save(curso));
    }

    // ── Eliminar ──────────────────────────────────────────────────────────────

    @Transactional
    public void eliminar(Long id) {
        Curso curso = buscarPorId(id);
        // Sin esta validación, el DELETE fallaría igual (hay una FK real
        // hacia Planificacion sin ON DELETE CASCADE, a propósito, para no
        // perder planificaciones reales al borrar un curso), pero con un
        // error genérico de integridad referencial en vez de un mensaje de
        // negocio claro (mismo patrón que PlanificacionService#eliminar).
        if (planificacionRepository.existsByCursoIdCurso(id)) {
            throw new ValidacionException(
                    "No se puede eliminar el curso \"" + curso.getNombre() +
                            "\": hay una planificación activa con ese curso. " +
                            "Elimina primero la planificación correspondiente.");
        }
        cursoRepository.deleteById(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Curso buscarPorId(Long id) {
        return cursoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el curso con id " + id + "."));
    }

    /**
     * Validaciones que Bean Validation (CursoRequest) no puede expresar
     * porque cruzan dos campos entre sí: cuposRestantes en relación a
     * cuposTotales. Las validaciones de campo individual (nombre no vacío,
     * horas > 0, etc.) ya las cubre @Valid en el controller antes de llegar
     * aquí; se dejan aquí solo por si algún día este método se llama desde
     * otro lugar que no pase por ese controller.
     */
    private void validar(String nombre, String codigo, Integer horas, BigDecimal costo,
                          Integer cuposTotales, Integer cuposRestantes, Modalidad modalidad) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El nombre del curso es obligatorio.");
        }
        if (codigo == null || codigo.trim().isEmpty()) {
            throw new ValidacionException("El código del curso es obligatorio.");
        }
        if (horas == null || horas <= 0) {
            throw new ValidacionException("Las horas deben ser un número mayor a 0.");
        }
        if (costo == null || costo.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidacionException("El costo debe ser un valor mayor o igual a 0.");
        }
        if (cuposTotales == null || cuposTotales <= 0) {
            throw new ValidacionException("Los cupos totales deben ser un número mayor a 0.");
        }
        if (cuposRestantes != null && (cuposRestantes < 0 || cuposRestantes > cuposTotales)) {
            throw new ValidacionException("Los cupos restantes no pueden ser negativos ni mayores a los cupos totales.");
        }
        if (modalidad == null) {
            throw new ValidacionException("La modalidad es obligatoria.");
        }
    }
}
