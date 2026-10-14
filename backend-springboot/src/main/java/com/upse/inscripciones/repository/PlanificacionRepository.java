package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.entity.EstadoCurso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanificacionRepository extends JpaRepository<Planificacion, Long>, JpaSpecificationExecutor<Planificacion> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Planificacion p WHERE p.id = :id")
    java.util.Optional<Planificacion> findByIdForUpdate(@Param("id") Long id);

    // ✅ NUEVO: usado en vez de findAll() por los listados que arman
    // PlanificacionResponse (necesita curso, coordinador Y especialista
    // completos). Con las relaciones ahora en LAZY, sin este JOIN FETCH
    // cada elemento del listado dispararía 3 SELECT adicionales (N+1).
    @Query("SELECT p FROM Planificacion p "
            + "JOIN FETCH p.curso "
            + "JOIN FETCH p.coordinador "
            + "JOIN FETCH p.especialista")
    List<Planificacion> findAllConDetalles();

    // Nota: la versión paginada con buscador/filtros ya no vive acá; ver
    // PlanificacionService#listarTodas(texto, modalidad, fechaDesde,
    // fechaHasta, pageable), que arma un Specification con JOIN FETCH
    // condicional (mismo patrón que InscripcionService#listarTodas).

    @Query("SELECT p FROM Planificacion p "
            + "JOIN FETCH p.curso JOIN FETCH p.coordinador JOIN FETCH p.especialista "
            + "WHERE p.especialista.id = :idEspecialista OR EXISTS "
            + "(SELECT d.id FROM Planificacion p2 JOIN p2.docentes d WHERE p2.id = p.id AND d.id = :idEspecialista) "
            + "ORDER BY p.fechaInicio DESC")
    List<Planificacion> findByEspecialistaId(@Param("idEspecialista") Long idEspecialista);

    @Query("SELECT p FROM Planificacion p "
            + "JOIN FETCH p.curso JOIN FETCH p.coordinador JOIN FETCH p.especialista "
            + "WHERE p.curso.idCurso = :idCurso")
    List<Planificacion> findByCursoId(@Param("idCurso") Long idCurso);

    // ✅ NUEVO: usados para bloquear la eliminación de un curso, coordinador
    // o especialista que todavía tiene una planificación activa asociada,
    // con un mensaje de negocio claro (ver *Service#eliminar), en vez de
    // dejar que falle la FK real (id_curso / id_coordinador / id_especialista
    // son NOT NULL) con un error genérico de integridad referencial.
    boolean existsByCursoIdCurso(Long idCurso);

    boolean existsByCoordinadorId(Long idCoordinador);

    boolean existsByEspecialistaId(Long idEspecialista);

    boolean existsByDocentesId(Long idEspecialista);

    // ✅ NUEVO: para EspecialistaService#recalcularEstado — "Activo" ahora
    // significa "está dictando clases hoy" (hay una planificación suya con
    // fechaInicio <= hoy <= fechaFin), tanto por el campo especialista
    // como por la lista de docentes de la planificación.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Planificacion p "
            + "WHERE (p.especialista.id = :idEspecialista OR EXISTS "
            + "(SELECT d.id FROM Planificacion p2 JOIN p2.docentes d WHERE p2.id = p.id AND d.id = :idEspecialista)) "
            + "AND p.fechaInicio <= :hoy AND p.fechaFin >= :hoy")
    boolean existeActivaParaEspecialista(@Param("idEspecialista") Long idEspecialista, @Param("hoy") java.time.LocalDate hoy);

    @Query("SELECT p FROM Planificacion p "
            + "JOIN FETCH p.curso JOIN FETCH p.coordinador JOIN FETCH p.especialista "
            + "WHERE p.coordinador.id = :idCoordinador ORDER BY p.fechaInicio DESC")
    List<Planificacion> findByCoordinadorId(@Param("idCoordinador") Long idCoordinador);

    // ✅ NUEVO: reemplaza a "findAllConDetalles() + filtrar en Java" para
    // el catálogo público (listarVigentes). Antes se traían TODAS las
    // planificaciones a memoria para descartar la mayoría ahí mismo; ahora
    // el filtro (no vencida Y con cupo) lo resuelve la base de datos.
    // ORDER BY p.id DESC: la planificación recién creada (mayor id) aparece
    // primero en el catálogo público, en vez de al final como antes (sin
    // ORDER BY, el orden dependía de cómo la BD devolviera las filas).
    @Query("SELECT p FROM Planificacion p "
            + "JOIN FETCH p.curso c JOIN FETCH p.coordinador JOIN FETCH p.especialista "
            + "WHERE p.fechaFin >= :hoy AND c.cuposRestantes > 0 AND c.estado <> :estadoFinalizado "
            + "ORDER BY p.id DESC")
    List<Planificacion> findVigentes(
            @Param("hoy") java.time.LocalDate hoy,
            @Param("estadoFinalizado") EstadoCurso estadoFinalizado);
}
