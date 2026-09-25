package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.EstadoCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CursoRepository extends JpaRepository<Curso, Long>, JpaSpecificationExecutor<Curso> {

    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByCodigoIgnoreCase(String codigo);
    List<Curso> findByEstado(EstadoCurso estado);

    /**
     * Resta 1 cupo de forma atómica, solo si todavía queda al menos 1
     * disponible. Al ser un único UPDATE condicional, la propia base de
     * datos serializa las peticiones concurrentes: si dos inscripciones
     * llegan al mismo tiempo con 1 cupo restante, la segunda ya no cumple
     * "cupos_restantes > 0" y no afecta ninguna fila, evitando la
     * sobreventa que existía con el patrón anterior de "leer, validar en
     * Java, guardar" en dos pasos separados.
     * <p>
     * Devuelve cuántas filas se modificaron: 1 = se descontó el cupo,
     * 0 = ya no había cupo disponible (el llamador debe tratar esto como
     * "sin cupo", no como un error de base de datos).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Curso c SET c.cuposRestantes = c.cuposRestantes - 1 " +
            "WHERE c.idCurso = :idCurso AND c.cuposRestantes > 0 " +
            "AND c.estado <> com.upse.inscripciones.entity.EstadoCurso.FINALIZADO")
    int decrementarCupoSiHayDisponible(@Param("idCurso") Long idCurso);

    /**
     * Libera 1 cupo de forma atómica (ej. al rechazar/cancelar una
     * inscripción que antes ocupaba cupo).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Curso c SET c.cuposRestantes = c.cuposRestantes + 1 " +
            "WHERE c.idCurso = :idCurso AND c.cuposRestantes < c.cuposTotales")
    void incrementarCupo(@Param("idCurso") Long idCurso);
}
