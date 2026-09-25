package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Especialista;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EspecialistaRepository extends JpaRepository<Especialista, Long>, JpaSpecificationExecutor<Especialista> {

    boolean existsByCedula(String cedula);
    boolean existsByCorreo(String correo);
    Optional<Especialista> findByCedula(String cedula);
    List<Especialista> findByEstadoTrue();
}
