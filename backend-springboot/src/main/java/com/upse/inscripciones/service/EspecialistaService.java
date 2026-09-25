package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.EspecialistaResponse;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.util.TelefonoUtils;
import com.upse.inscripciones.repository.EspecialistaRepository;
import com.upse.inscripciones.util.Nacionalidades;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EspecialistaService {

    private final EspecialistaRepository especialistaRepository;
    private final InformeEconomicoService informes;
    private final com.upse.inscripciones.repository.InformeEconomicoRepository informesRepository;
    private final com.upse.inscripciones.repository.PlanificacionRepository planificacionRepository;

    private static final Pattern PASAPORTE_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern CEDULA_PATTERN   = Pattern.compile("^\\d{10}$");
    private static final Pattern EMAIL_PATTERN     =
            Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    // ── Listar ────────────────────────────────────────────────────────────────

    public List<EspecialistaResponse> listarTodos() {
        return especialistaRepository.findAll()
                .stream()
                .map(EspecialistaResponse::fromEntity)
                .toList();
    }

    // Listado paginado para especialistas.html, con búsqueda opcional (nombres,
    // apellidos, cédula, especialidad o área de conocimiento) y filtro de
    // estado. Se arma con Specification, igual que CursoService#listarTodos,
    // para evitar el bug de Hibernate con bind de parámetro null + Pageable.
    // "área de conocimiento" es texto libre (no un catálogo cerrado), así
    // que se busca dentro del mismo cuadro de texto en vez de un select
    // aparte: con muchos especialistas ese select tendría demasiadas variantes
    // (typos, sinónimos) para ser un filtro útil.
    public Page<EspecialistaResponse> listarPaginado(String texto, Boolean estado, String nacionalidad, Pageable pageable) {
        return especialistaRepository.findAll(construirSpecification(texto, estado, nacionalidad), pageable)
                .map(EspecialistaResponse::fromEntity);
    }

    private Specification<Especialista> construirSpecification(String texto, Boolean estado, String nacionalidad) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();
        String nacionalidadNormalizada = (nacionalidad == null || nacionalidad.isBlank())
                ? null : nacionalidad.trim().toUpperCase();

        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) {
                condiciones.add(cb.equal(root.get("estado"), estado));
            }
            if (nacionalidadNormalizada != null) {
                condiciones.add(cb.equal(root.get("paisNacionalidad"), nacionalidadNormalizada));
            }
            if (textoNormalizado != null) {
                String patron = "%" + textoNormalizado + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombres")), patron),
                        cb.like(cb.lower(root.get("apellidos")), patron),
                        cb.like(cb.lower(root.get("cedula")), patron),
                        cb.like(cb.lower(root.get("especialidad")), patron),
                        cb.like(cb.lower(root.get("areaConocimiento")), patron)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    // ✅ NUEVO: para el botón "Exportar a Excel" de especialistas.html.
    public List<EspecialistaResponse> listarParaExportar(String texto, Boolean estado, String nacionalidad) {
        return especialistaRepository.findAll(
                        construirSpecification(texto, estado, nacionalidad),
                        org.springframework.data.domain.Sort.by("id").descending())
                .stream()
                .map(EspecialistaResponse::fromEntity)
                .toList();
    }

    public List<EspecialistaResponse> listarActivos() {
        return especialistaRepository.findByEstadoTrue()
                .stream()
                .map(EspecialistaResponse::fromEntity)
                .toList();
    }

    public EspecialistaResponse obtenerPorId(Long id) {
        return EspecialistaResponse.fromEntity(buscarPorId(id));
    }

    // ── Registrar ─────────────────────────────────────────────────────────────

    @Transactional
    public EspecialistaResponse registrar(String cedula, String nombres, String apellidos,
                                     String telefono, String correo,
                                     String especialidad, String areaConocimiento,
                                     String paisNacionalidad) {
        validar(cedula, nombres, apellidos, telefono, correo);
        String paisValidado = validarNacionalidad(paisNacionalidad);

        if (especialistaRepository.existsByCedula(cedula.trim())) {
            throw new ValidacionException("Ya existe un especialista registrado con la cédula " + cedula + ".");
        }
        String correoNormalizado = normalizarCorreo(correo);
        if (correoNormalizado != null && especialistaRepository.existsByCorreo(correoNormalizado)) {
            throw new ValidacionException("Ya existe un especialista registrado con el correo " + correo + ".");
        }

        Especialista especialista = Especialista.builder()
                .cedula(cedula.trim())
                .nombres(nombres.trim())
                .apellidos(apellidos.trim())
                .telefono(telefono == null || telefono.isBlank() ? null : TelefonoUtils.normalizar(telefono))
                .correo(correoNormalizado)
                .especialidad(especialidad == null ? null : especialidad.trim())
                .areaConocimiento(areaConocimiento == null ? null : areaConocimiento.trim())
                .paisNacionalidad(paisValidado)
                .build();

        return EspecialistaResponse.fromEntity(especialistaRepository.save(especialista));
    }

    // ── Actualizar ────────────────────────────────────────────────────────────

    @Transactional
    public EspecialistaResponse actualizar(Long id, String cedula, String nombres, String apellidos,
                                      String telefono, String correo,
                                      String especialidad, String areaConocimiento,
                                      String paisNacionalidad) {
        Especialista especialista = buscarPorId(id);
        validar(cedula, nombres, apellidos, telefono, correo);
        String paisValidado = validarNacionalidad(paisNacionalidad);

        // Verificar duplicados solo si cambian los valores únicos
        if (!especialista.getCedula().equals(cedula.trim()) && especialistaRepository.existsByCedula(cedula.trim())) {
            throw new ValidacionException("Ya existe otro especialista con la cédula " + cedula + ".");
        }
        String correoNormalizado = normalizarCorreo(correo);
        if (correoNormalizado != null
                && (especialista.getCorreo() == null || !especialista.getCorreo().equals(correoNormalizado))
                && especialistaRepository.existsByCorreo(correoNormalizado)) {
            throw new ValidacionException("Ya existe otro especialista con el correo " + correo + ".");
        }

        if (paisValidado != null && !"EC".equals(paisValidado)
                && !java.util.Objects.equals(paisValidado, especialista.getPaisNacionalidad())) {
            for(var p : planificacionRepository.findByEspecialistaId(id)) {
                boolean historico=informesRepository.findByPlanificacionId(p.getId()).map(i->Boolean.TRUE.equals(i.getHistorico())).orElse(false);
                if(!historico && p.getDocentesEfectivos().size()>1 && HonorariosEspecialistas.desglosar(p).isEmpty())
                    throw new ValidacionException("Antes de cambiar la nacionalidad, desglosa los honorarios por especialista en la planificación " + p.getId() + ". No se puede calcular la transferencia de un total compartido.");
            }
        }
        especialista.setCedula(cedula.trim());
        especialista.setNombres(nombres.trim());
        especialista.setApellidos(apellidos.trim());
        especialista.setTelefono(telefono == null || telefono.isBlank() ? null : TelefonoUtils.normalizar(telefono));
        especialista.setCorreo(correoNormalizado);
        especialista.setEspecialidad(especialidad == null ? null : especialidad.trim());
        especialista.setAreaConocimiento(areaConocimiento == null ? null : areaConocimiento.trim());
        especialista.setPaisNacionalidad(paisValidado);

        especialistaRepository.saveAndFlush(especialista);
        for(var p:planificacionRepository.findByEspecialistaId(id))informes.recalcularYGuardar(p.getId());
        return EspecialistaResponse.fromEntity(especialista);
    }

    // ── Desactivar / Activar ──────────────────────────────────────────────────

    @Transactional
    public EspecialistaResponse cambiarEstado(Long id, boolean estado) {
        Especialista especialista = buscarPorId(id);
        especialista.setEstado(estado);
        return EspecialistaResponse.fromEntity(especialistaRepository.save(especialista));
    }

    // ── Activo / Disponible (automático) ───────────────────────────────────────
    // "Activo" = está dictando clases hoy (tiene una planificación con
    // fechaInicio <= hoy <= fechaFin); "Disponible" = no está dictando
    // ninguna en este momento. Ya no es un interruptor manual: se recalcula
    // solo, desde PlanificacionService (al guardar/eliminar una
    // planificación) y desde EspecialistaEstadoScheduler (una vez al día,
    // para el caso en que una planificación empieza o termina sin que nadie
    // haga ninguna acción ese día).

    @Transactional
    public void recalcularEstado(Long id) {
        especialistaRepository.findById(id).ifPresent(this::recalcularEstadoInterno);
    }

    @Transactional
    public void recalcularEstado(java.util.Collection<Long> ids) {
        ids.stream().distinct().forEach(this::recalcularEstado);
    }

    @Transactional
    public void recalcularEstadoDeTodos() {
        especialistaRepository.findAll().forEach(this::recalcularEstadoInterno);
    }

    private void recalcularEstadoInterno(Especialista especialista) {
        boolean activo = planificacionRepository.existeActivaParaEspecialista(especialista.getId(), java.time.LocalDate.now());
        if (!java.util.Objects.equals(especialista.getEstado(), activo)) {
            especialista.setEstado(activo);
            especialistaRepository.save(especialista);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        Especialista especialista = buscarPorId(id);
        // Mismo caso que en CursoService#eliminar: hay una FK real hacia
        // Planificacion (id_especialista NOT NULL) sin ON DELETE CASCADE.
        if (planificacionRepository.existsByEspecialistaId(id) || planificacionRepository.existsByDocentesId(id)) {
            throw new ValidacionException(
                    "No se puede eliminar al especialista \"" + especialista.getNombres() + " " +
                            especialista.getApellidos() + "\": hay una planificación activa asignada a él. " +
                            "Elimina o reasigna primero esa planificación.");
        }
        especialistaRepository.deleteById(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Especialista buscarPorId(Long id) {
        return especialistaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el especialista con id " + id + "."));
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

    // Código vacío = "no especificado", válido (el campo es opcional). Si
    // viene algo, debe existir en el catálogo, para que nunca se guarde un
    // texto suelto que después no se pueda filtrar de forma exacta.
    private String validarNacionalidad(String paisNacionalidad) {
        if (paisNacionalidad == null || paisNacionalidad.isBlank()) {
            return null;
        }
        String codigo = paisNacionalidad.trim().toUpperCase();
        if (!Nacionalidades.esCodigoValido(codigo)) {
            throw new ValidacionException("La nacionalidad seleccionada no es válida.");
        }
        return codigo;
    }

    private String normalizarCorreo(String correo) {
        return correo == null || correo.isBlank() ? null : correo.trim().toLowerCase();
    }
}
