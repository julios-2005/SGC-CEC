package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.InformeEconomico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InformeEconomicoRepository extends JpaRepository<InformeEconomico, Long> {

    @Query("SELECT ie FROM InformeEconomico ie WHERE ie.planificacion.id = :idPlanificacion")
    Optional<InformeEconomico> findByPlanificacionId(@Param("idPlanificacion") Long idPlanificacion);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ie FROM InformeEconomico ie WHERE ie.id = :id")
    Optional<InformeEconomico> findByIdForUpdate(@Param("id") Long id);
    // ✅ NUEVO: usado en vez de findAll() por listarResumen(), que arma
    // InformeEconomicoResumenResponse (necesita planificacion.curso.nombre).
    // Con la relación ahora en LAZY, sin este JOIN FETCH cada fila del
    // listado dispararía 2 SELECT adicionales (N+1).
    @Query("SELECT ie FROM InformeEconomico ie LEFT JOIN FETCH ie.planificacion p LEFT JOIN FETCH p.curso")
    List<InformeEconomico> findAllConDetalles();
}
