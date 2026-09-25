package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Coordinador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CoordinadorRepository extends JpaRepository<Coordinador, Long>, JpaSpecificationExecutor<Coordinador> {

    boolean existsByCedula(String cedula);
    boolean existsByCorreo(String correo);
    Optional<Coordinador> findByCedula(String cedula);
    List<Coordinador> findByEstadoTrue();
}
