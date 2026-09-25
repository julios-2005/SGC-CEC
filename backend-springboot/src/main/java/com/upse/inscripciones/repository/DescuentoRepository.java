package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Descuento;
import com.upse.inscripciones.entity.TipoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DescuentoRepository extends JpaRepository<Descuento, Long> {

    boolean existsByNombre(String nombre);
    List<Descuento> findByEstadoTrue();
    Optional<Descuento> findByTipoUsuario(TipoUsuario tipoUsuario);
    boolean existsByTipoUsuario(TipoUsuario tipoUsuario);
}
