package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Inscripcion;
import com.upse.inscripciones.entity.EstadoInscripcion;
import com.upse.inscripciones.entity.TipoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long>, JpaSpecificationExecutor<Inscripcion> {

    // Serializa las revisiones simultáneas para no liberar/ocupar el mismo cupo dos veces.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inscripcion i WHERE i.id = :id")
    Optional<Inscripcion> findByIdForUpdate(@Param("id") Long id);

    boolean existsByCedula(String cedula);

    Optional<Inscripcion> findByCedula(String cedula);

    // Coincide con el índice único parcial real de la base de datos
    // (uk_inscripcion_cedula_planificacion_activa, ver
    // db/migration/V1__baseline_esquema_actual.sql):
    // un mismo estudiante SÍ puede volver a inscribirse en la misma
    // planificación si su inscripción anterior quedó RECHAZADA o RETIRADA
    // (ninguna de las dos ocupa cupo); solo se bloquea si ya tiene una
    // inscripción PENDIENTE o ACEPTADA para esa misma planificación.
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Inscripcion i "
            + "WHERE i.cedula = :cedula AND i.planificacion.id = :idPlanificacion "
            + "AND i.estado IN (com.upse.inscripciones.entity.EstadoInscripcion.PENDIENTE, "
            + "com.upse.inscripciones.entity.EstadoInscripcion.ACEPTADA)")
    boolean existsByCedulaAndPlanificacionId(
            @Param("cedula") String cedula, @Param("idPlanificacion") Long idPlanificacion);

    List<Inscripcion> findByTipoUsuario(TipoUsuario tipoUsuario);


    // ✅ NUEVO: útil para ver todos los inscritos de una planificación específica
    @Query("SELECT i FROM Inscripcion i JOIN FETCH i.planificacion p JOIN FETCH p.curso "
            + "WHERE p.id = :idPlanificacion")
    List<Inscripcion> findByPlanificacionId(@Param("idPlanificacion") Long idPlanificacion);

    // ✅ NUEVO: usado por el Informe Económico. Solo las inscripciones
    // ACEPTADAS cuentan como participante/ingreso (definido con el usuario).
    @Query("SELECT i FROM Inscripcion i WHERE i.planificacion.id = :idPlanificacion "
            + "AND i.estado = com.upse.inscripciones.entity.EstadoInscripcion.ACEPTADA")
    List<Inscripcion> findAceptadasByPlanificacionId(@Param("idPlanificacion") Long idPlanificacion);

    // ✅ NUEVO: usado por PlanificacionService#eliminar para dar un mensaje
    // de negocio claro en vez de dejar que falle por integridad referencial
    // (no hay ON DELETE CASCADE entre Inscripcion y Planificacion a propósito:
    // borrar una planificación con inscripciones reales sería pérdida de datos).
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Inscripcion i "
            + "WHERE i.planificacion.id = :idPlanificacion")
    boolean existsByPlanificacionId(@Param("idPlanificacion") Long idPlanificacion);
}
