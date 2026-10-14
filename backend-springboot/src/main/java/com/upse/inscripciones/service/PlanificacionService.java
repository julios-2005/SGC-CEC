package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.PlanificacionResponse;
import com.upse.inscripciones.entity.Coordinador;
import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.repository.CoordinadorRepository;
import com.upse.inscripciones.repository.CursoRepository;
import com.upse.inscripciones.repository.EspecialistaRepository;
import com.upse.inscripciones.repository.InscripcionRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanificacionService {

    private final PlanificacionRepository planificacionRepository;
    private final CoordinadorRepository coordinadorRepository;  // ✅ NUEVO
    private final EspecialistaRepository especialistaRepository;          // ✅ NUEVO
    private final CursoRepository cursoRepository;              // ✅ NUEVO
    private final InscripcionRepository inscripcionRepository;  // ✅ NUEVO: validar antes de eliminar
    private final InformeEconomicoService informeEconomicoService;
    private final DocentesService docentesService;
    private final EspecialistaService especialistaService;

    @Transactional
    public PlanificacionResponse guardarConDocentes(Long id, Long idCurso, Long idCoordinador,
            Long idEspecialista, LocalDate fechaInicio, String horario, LocalDate fechaFin,
            Modalidad modalidad, java.math.BigDecimal costoEspecialista, List<Long> docenteIds) {
        return guardarConDocentes(id,idCurso,idCoordinador,idEspecialista,fechaInicio,horario,fechaFin,modalidad,costoEspecialista,docenteIds,null);
    }
    @Transactional
    public PlanificacionResponse guardarConDocentes(Long id, Long idCurso, Long idCoordinador,
            Long idEspecialista, LocalDate fechaInicio, String horario, LocalDate fechaFin,
            Modalidad modalidad, java.math.BigDecimal costoEspecialista, List<Long> docenteIds,
            List<java.math.BigDecimal> honorariosDocentes) {
        validarCampos(idCurso,idCoordinador,idEspecialista,fechaInicio,horario,fechaFin,modalidad);
        validarCostoEspecialista(costoEspecialista);
        Planificacion anterior=id==null?null:planificacionRepository.findById(id)
                .orElseThrow(()->new RecursoNoEncontradoException("Planificación no encontrada."));
        var anteriores=anterior==null?List.<Especialista>of():anterior.getDocentesEfectivos();
        List<Long> ids=docenteIds;
        if(ids==null && id==null){
            ids=List.of(idEspecialista);
        }else if(ids==null && !anteriores.isEmpty() && !anteriores.get(0).getId().equals(idEspecialista)){ids=List.of(idEspecialista);}
        var docentes=docentesService.resolver(ids,anteriores);
        if(docentes.isEmpty())throw new ValidacionException("Selecciona al menos un docente.");
        if(anterior!=null && !anterior.getCurso().getIdCurso().equals(idCurso) && inscripcionRepository.existsByPlanificacionId(id))
            throw new ValidacionException("No se puede cambiar el curso: esta planificación tiene inscripciones registradas.");
        var valores=HonorariosEspecialistas.validar(docentes,honorariosDocentes,costoEspecialista,anterior==null?null:anterior.getHonorarios());
        var entidades=resolverYValidarEntidadesRelacionadas(idCurso,idCoordinador,docentes.get(0).getId(),anterior);
        var p=anterior==null?Planificacion.builder().build():anterior;
        p.setCurso(entidades.curso());p.setCoordinador(entidades.coordinador());p.setEspecialista(entidades.especialista());
        p.setFechaInicio(fechaInicio);p.setFechaFin(fechaFin);p.setHorario(horario.trim());p.setModalidad(modalidad);
        p.getDocentes().clear();p.getDocentes().addAll(docentes);
        p.getHonorarios().clear();p.getHonorarios().putAll(valores);
        p.setCostoEspecialista(valores.isEmpty()?costoEspecialista:valores.values().stream().reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add));
        p=planificacionRepository.saveAndFlush(p);
        informeEconomicoService.recalcularYGuardar(p.getId());
        // Ver EspecialistaService#recalcularEstado: cualquiera que haya
        // dejado de estar en la lista de docentes, o que se haya sumado,
        // puede cambiar entre Activo/Disponible con este guardado.
        var idsParaRecalcular=new java.util.HashSet<Long>();
        anteriores.forEach(e->idsParaRecalcular.add(e.getId()));
        docentes.forEach(e->idsParaRecalcular.add(e.getId()));
        especialistaService.recalcularEstado(idsParaRecalcular);
        return PlanificacionResponse.fromEntity(p);
    }

    @Transactional
    public PlanificacionResponse registrarPlanificacion(
            Long idCurso,                // ✅ CAMBIO: Antes era String actividad
            Long idCoordinador,          // ✅ CAMBIO: Ahora es ID
            Long idEspecialista,              // ✅ CAMBIO: Ahora es ID
            LocalDate fechaInicio,
            String horario,
            LocalDate fechaFin,
            Modalidad modalidad,
            java.math.BigDecimal costoEspecialista) {

        // ✅ Validar campos
        validarCampos(idCurso, idCoordinador, idEspecialista, fechaInicio, horario, fechaFin, modalidad);
        validarCostoEspecialista(costoEspecialista);

        // ✅ Recuperar y validar las entidades relacionadas (existencia + activas)
        EntidadesRelacionadas entidades = resolverYValidarEntidadesRelacionadas(idCurso, idCoordinador, idEspecialista, null);

        Planificacion planificacion = Planificacion.builder()
                .curso(entidades.curso())                    // ✅ Asignar objeto completo
                .coordinador(entidades.coordinador())        // ✅ Asignar objeto completo
                .especialista(entidades.especialista())                // ✅ Asignar objeto completo
                .fechaInicio(fechaInicio)
                .horario(horario.trim())
                .fechaFin(fechaFin)
                .modalidad(modalidad)
                .costoEspecialista(costoEspecialista)
                .build();

        Planificacion guardada = planificacionRepository.save(planificacion);

        // Genera de una vez el Informe Económico inicial (0 participantes) para
        // que la planificación ya aparezca en el listado del informe.
        informeEconomicoService.recalcularYGuardar(guardada.getId());

        return PlanificacionResponse.fromEntity(guardada);
    }

    @Transactional
    public PlanificacionResponse actualizarPlanificacion(
            Long id,
            Long idCurso,
            Long idCoordinador,
            Long idEspecialista,
            LocalDate fechaInicio,
            String horario,
            LocalDate fechaFin,
            Modalidad modalidad,
            java.math.BigDecimal costoEspecialista) {

        Planificacion planificacion = planificacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la planificación con id " + id));

        validarCampos(idCurso, idCoordinador, idEspecialista, fechaInicio, horario, fechaFin, modalidad);
        validarCostoEspecialista(costoEspecialista);

        if (!planificacion.getCurso().getIdCurso().equals(idCurso)
                && inscripcionRepository.existsByPlanificacionId(id)) {
            throw new ValidacionException(
                    "No se puede cambiar el curso de esta planificación porque ya tiene inscripciones registradas. "
                            + "Puedes modificar las demás propiedades o crear una nueva planificación.");
        }

        EntidadesRelacionadas entidades = resolverYValidarEntidadesRelacionadas(idCurso, idCoordinador, idEspecialista, planificacion);

        planificacion.setCurso(entidades.curso());
        planificacion.setCoordinador(entidades.coordinador());
        planificacion.setEspecialista(entidades.especialista());
        planificacion.setFechaInicio(fechaInicio);
        planificacion.setHorario(horario.trim());
        planificacion.setFechaFin(fechaFin);
        planificacion.setModalidad(modalidad);
        planificacion.setCostoEspecialista(costoEspecialista);

        Planificacion guardada = planificacionRepository.save(planificacion);

        // El curso (y por lo tanto su costo) y/o el costo del especialista pueden
        // haber cambiado: hay que recalcular el Informe Económico.
        informeEconomicoService.recalcularYGuardar(guardada.getId());

        return PlanificacionResponse.fromEntity(guardada);
    }

    public List<PlanificacionResponse> listarTodas() {
        return planificacionRepository.findAllConDetalles()
                .stream()
                .map(PlanificacionResponse::fromEntity)
                .toList();
    }

    // ✅ NUEVO: versión paginada para PlanificacionController#listarTodas
    // (pantalla de gestión), sin filtros — mantiene compatibilidad si algo
    // más la sigue llamando así.
    public Page<PlanificacionResponse> listarTodas(Pageable pageable) {
        return listarTodas(null, null, null, null, null, pageable);
    }

    // ✅ NUEVO: con buscador de texto (nombre del curso, coordinador o
    // especialista), filtro de modalidad, rango de fecha de inicio y estado
    // (Activa/Finalizada). Se arma con Specification en vez del antiguo
    // "findAllConDetalles(Pageable)" con @Query fijo: mismo patrón (y mismo
    // motivo, ver el comentario largo) que InscripcionService#listarTodas —
    // el join a curso/coordinador/especialista se usa como "fetch" solo
    // para la query de datos (evita el N+1 que traería LAZY), y como "join"
    // simple para la query de COUNT.
    // Overload de compatibilidad sin filtro de estado (Activa/Finalizada).
    @Transactional
    public Page<PlanificacionResponse> listarTodas(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta, Pageable pageable) {
        return listarTodas(texto, modalidad, fechaDesde, fechaHasta, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PlanificacionResponse> listarTodas(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta,
            String estadoPlanificacion, Pageable pageable) {
        String q = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();
        String estado = estadoPlanificacion == null ? null : estadoPlanificacion.trim().toUpperCase();

        List<PlanificacionResponse> filtradas = planificacionRepository.findAllConDetalles().stream()
                .filter(p -> modalidad == null || p.getModalidad() == modalidad)
                .filter(p -> fechaDesde == null || !p.getFechaInicio().isBefore(fechaDesde))
                .filter(p -> fechaHasta == null || !p.getFechaInicio().isAfter(fechaHasta))
                .filter(p -> {
                    if ("ACTIVA".equals(estado)) return !p.getFechaFin().isBefore(LocalDate.now());
                    if ("FINALIZADA".equals(estado)) return p.getFechaFin().isBefore(LocalDate.now());
                    return true;
                })
                .filter(p -> {
                    if (q == null) return true;
                    String textoPlan = String.join(" ",
                            p.getCurso().getNombre(),
                            p.getCoordinador().getNombres(), p.getCoordinador().getApellidos(),
                            p.getEspecialista().getNombres(), p.getEspecialista().getApellidos(),
                            p.getNombresDocentes()).toLowerCase();
                    return textoPlan.contains(q);
                })
                .map(PlanificacionResponse::fromEntity)
                .toList();

        int inicio = Math.min((int) pageable.getOffset(), filtradas.size());
        int fin = Math.min(inicio + pageable.getPageSize(), filtradas.size());
        return new org.springframework.data.domain.PageImpl<>(filtradas.subList(inicio, fin), pageable, filtradas.size());
    }

    // La misma Specification sirve tanto para el listado paginado (donde
    // Spring Data también la usa para armar la query de COUNT) como para el
    // export sin paginar (que nunca ejecuta esa rama porque no hay COUNT).
    private Specification<Planificacion> construirSpecification(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta,
            String estadoPlanificacion) {
        String textoNormalizado = (texto == null || texto.isBlank()) ? null : texto.trim().toLowerCase();
        // "ACTIVA" = todavía no termina (fechaFin >= hoy, igual que
        // PlanificacionResponse#isActiva y que PlanificacionRepository#findVigentes);
        // "FINALIZADA" = ya terminó (fechaFin < hoy). Cualquier otro valor
        // (incluido null/blank) no filtra por estado.
        String estadoNormalizado = estadoPlanificacion == null ? null : estadoPlanificacion.trim().toUpperCase();

        return (root, query, cb) -> {
            boolean esQueryDeConteo = query.getResultType() == Long.class || query.getResultType() == long.class;

            Join<Planificacion, Curso> cursoJoin;
            Join<Planificacion, Coordinador> coordinadorJoin;
            Join<Planificacion, Especialista> especialistaJoin;
            if (esQueryDeConteo) {
                cursoJoin = root.join("curso", JoinType.INNER);
                coordinadorJoin = root.join("coordinador", JoinType.INNER);
                especialistaJoin = root.join("especialista", JoinType.INNER);
            } else {
                cursoJoin = (Join<Planificacion, Curso>) (Join) root.fetch("curso", JoinType.INNER);
                coordinadorJoin = (Join<Planificacion, Coordinador>) (Join) root.fetch("coordinador", JoinType.INNER);
                especialistaJoin = (Join<Planificacion, Especialista>) (Join) root.fetch("especialista", JoinType.INNER);
            }

            List<Predicate> condiciones = new ArrayList<>();
            if (modalidad != null) {
                condiciones.add(cb.equal(root.get("modalidad"), modalidad));
            }
            if (fechaDesde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaInicio"), fechaDesde));
            }
            if (fechaHasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaInicio"), fechaHasta));
            }
            if ("ACTIVA".equals(estadoNormalizado)) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaFin"), LocalDate.now()));
            } else if ("FINALIZADA".equals(estadoNormalizado)) {
                condiciones.add(cb.lessThan(root.get("fechaFin"), LocalDate.now()));
            }
            if (textoNormalizado != null) {
                String patron = "%" + textoNormalizado + "%";
                var docentesQuery = query.subquery(Long.class);
                var planDocentes = docentesQuery.from(Planificacion.class);
                var docente = planDocentes.join("docentes");
                docentesQuery.select(planDocentes.get("id")).where(
                        cb.equal(planDocentes.get("id"), root.get("id")),
                        cb.or(cb.like(cb.lower(docente.get("nombres")), patron),
                                cb.like(cb.lower(docente.get("apellidos")), patron)));
                condiciones.add(cb.or(
                        cb.like(cb.lower(cursoJoin.get("nombre")), patron),
                        cb.like(cb.lower(coordinadorJoin.get("nombres")), patron),
                        cb.like(cb.lower(coordinadorJoin.get("apellidos")), patron),
                        cb.like(cb.lower(especialistaJoin.get("nombres")), patron),
                        cb.like(cb.lower(especialistaJoin.get("apellidos")), patron),
                        cb.exists(docentesQuery)));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    // Overload de compatibilidad sin filtro de estado (Activa/Finalizada).
    public List<PlanificacionResponse> listarParaExportar(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta) {
        return listarParaExportar(texto, modalidad, fechaDesde, fechaHasta, null);
    }

    // ✅ NUEVO: para el botón "Exportar a Excel" de planificaciones.html.
    @SuppressWarnings("unchecked")
    public List<PlanificacionResponse> listarParaExportar(
            String texto, Modalidad modalidad, LocalDate fechaDesde, LocalDate fechaHasta,
            String estadoPlanificacion) {
        return planificacionRepository.findAll(
                        construirSpecification(texto, modalidad, fechaDesde, fechaHasta, estadoPlanificacion),
                        org.springframework.data.domain.Sort.by("fechaRegistro").descending())
                .stream()
                .map(PlanificacionResponse::fromEntity)
                .toList();
    }

    // ✅ NUEVO: para el catálogo público (cursos-disponibles.html e
    // inscripcion-form.html). Solo planificaciones que todavía no
    // terminaron Y que aún tienen cupo. El filtro ahora lo resuelve la
    // base de datos (antes: traer TODAS las filas y descartar la mayoría
    // en Java).
    public List<PlanificacionResponse> listarVigentes() {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        return planificacionRepository.findVigentes(hoy, EstadoCurso.FINALIZADO)
                .stream()
                .map(PlanificacionResponse::fromEntity)
                .toList();
    }

    // ✅ NUEVO: para la pantalla "Consulta Especialistas" (especialistas.html →
    // consulta-especialistas.html). Cursos dictados por un especialista puntual.
    public List<PlanificacionResponse> listarPorEspecialista(Long idEspecialista) {
        return planificacionRepository.findByEspecialistaId(idEspecialista)
                .stream()
                .map(PlanificacionResponse::fromEntity)
                .toList();
    }

    // ✅ NUEVO: para la pantalla "Consulta Coordinadores" (coordinadores.html →
    // consulta-coordinadores.html). Cursos a cargo de un coordinador puntual.
    public List<PlanificacionResponse> listarPorCoordinador(Long idCoordinador) {
        return planificacionRepository.findByCoordinadorId(idCoordinador)
                .stream()
                .map(PlanificacionResponse::fromEntity)
                .toList();
    }

    public PlanificacionResponse obtenerPorId(Long id) {
        Planificacion planificacion = planificacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la planificación con id " + id));
        return PlanificacionResponse.fromEntity(planificacion);
    }

    @Transactional
    public PlanificacionResponse actualizarCostoEspecialista(Long id, java.math.BigDecimal costoEspecialista) {
        validarCostoEspecialista(costoEspecialista);
        Planificacion planificacion = planificacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la planificación con id " + id));
        if(planificacion.getDocentesEfectivos().size()>1 && !planificacion.getHonorarios().isEmpty())
            throw new ValidacionException("Edita los honorarios individuales en el formulario de Planificaciones.");
        if(planificacion.getDocentesEfectivos().size()==1){
            planificacion.getHonorarios().clear();planificacion.getHonorarios().put(planificacion.getEspecialista().getId(),
                    costoEspecialista==null?java.math.BigDecimal.ZERO:costoEspecialista);
        }
        planificacion.setCostoEspecialista(costoEspecialista);
        Planificacion guardada = planificacionRepository.save(planificacion);

        // El costo del especialista es el egreso del Informe Económico: hay que recalcular.
        informeEconomicoService.recalcularYGuardar(guardada.getId());

        return PlanificacionResponse.fromEntity(guardada);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!planificacionRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("No se encontró la planificación con id " + id);
        }
        // Sin esta validación, el DELETE fallaría igual (hay una FK real
        // hacia Inscripcion sin ON DELETE CASCADE, a propósito, para no
        // perder inscripciones reales al borrar una planificación), pero
        // con un error genérico de integridad referencial en vez de un
        // mensaje de negocio claro.
        if (inscripcionRepository.existsByPlanificacionId(id)) {
            throw new ValidacionException(
                    "No se puede eliminar la planificación: tiene inscripciones registradas.");
        }
        var docentesAfectados=planificacionRepository.findById(id)
                .map(Planificacion::getDocentesEfectivos).orElse(List.of())
                .stream().map(Especialista::getId).toList();
        planificacionRepository.deleteById(id);
        especialistaService.recalcularEstado(docentesAfectados);
    }

    /**
     * Agrupa el resultado de resolverYValidarEntidadesRelacionadas: las tres
     * entidades relacionadas de una planificación, ya confirmadas como
     * existentes y (coordinador/especialista) activas.
     */
    private record EntidadesRelacionadas(Curso curso, Coordinador coordinador, Especialista especialista) {
    }

    /**
     * Busca curso, coordinador y especialista por id y valida que
     * coordinador/especialista estén activos. Antes esta misma secuencia de
     * 3 búsquedas + 2 validaciones de "estado activo" estaba copiada tal
     * cual en registrarPlanificacion y actualizarPlanificacion (hallazgo de
     * auditoría de duplicación menor); centralizarla acá evita que una
     * futura tercera regla de negocio sobre estas entidades (ej. validar
     * también que el curso no esté FINALIZADO) se aplique en un método y se
     * olvide en el otro.
     */
    private EntidadesRelacionadas resolverYValidarEntidadesRelacionadas(
            Long idCurso, Long idCoordinador, Long idEspecialista, Planificacion anterior) {

        Curso curso = cursoRepository.findById(idCurso)
                .orElseThrow(() -> new RecursoNoEncontradoException("Curso no encontrado con id: " + idCurso));

        Coordinador coordinador = coordinadorRepository.findById(idCoordinador)
                .orElseThrow(() -> new RecursoNoEncontradoException("Coordinador no encontrado con id: " + idCoordinador));

        Especialista especialista = especialistaRepository.findById(idEspecialista)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialista no encontrado con id: " + idEspecialista));

        if (!coordinador.getEstado() && (anterior == null || !anterior.getCoordinador().getId().equals(idCoordinador))) {
            throw new ValidacionException("El coordinador seleccionado no está activo.");
        }
        // El estado del especialista ("Activo"/"Disponible") ahora es un
        // reflejo automático de si está dictando clases ahora mismo, no un
        // permiso de cuenta habilitada/deshabilitada — así que NO bloquea
        // asignarlo a una planificación nueva. Ver EspecialistaService
        // #recalcularEstado, llamado tras guardar/eliminar cada
        // planificación.

        return new EntidadesRelacionadas(curso, coordinador, especialista);
    }

    private void validarCampos(Long idCurso, Long idCoordinador, Long idEspecialista,
                               LocalDate fechaInicio, String horario, LocalDate fechaFin,
                               Modalidad modalidad) {
        if (idCurso == null) {
            throw new ValidacionException("El Curso (actividad) es obligatorio.");
        }
        if (idCoordinador == null) {
            throw new ValidacionException("El Coordinador es obligatorio.");
        }
        if (idEspecialista == null) {
            throw new ValidacionException("El Especialista es obligatorio.");
        }
        if (fechaInicio == null) {
            throw new ValidacionException("La Fecha de Inicio es obligatoria.");
        }
        if (horario == null || horario.trim().isEmpty()) {
            throw new ValidacionException("El Horario es obligatorio.");
        }
        if (horario.trim().length() > 100) {
            throw new ValidacionException("El Horario no puede superar los 100 caracteres.");
        }
        if (fechaFin == null) {
            throw new ValidacionException("La Fecha de Fin es obligatoria.");
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new ValidacionException("La Fecha de Fin no puede ser anterior a la Fecha de Inicio.");
        }
        if (modalidad == null) {
            throw new ValidacionException("Debe seleccionar la Modalidad.");
        }
    }

    private void validarCostoEspecialista(java.math.BigDecimal costo) {
        if (costo != null && (costo.signum() < 0 || costo.compareTo(new java.math.BigDecimal("99999999.99")) > 0
                || costo.stripTrailingZeros().scale() > 2)) {
            throw new ValidacionException("El costo del especialista debe estar entre 0 y 99999999.99, con hasta 2 decimales.");
        }
    }
}
