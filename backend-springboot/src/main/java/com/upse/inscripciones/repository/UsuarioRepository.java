package com.upse.inscripciones.repository;

import com.upse.inscripciones.entity.Usuario;
import com.upse.inscripciones.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    /**
     * Busca un usuario por nombreUsuario
     */
    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    /**
     * Busca un usuario por coordinador
     */
    Optional<Usuario> findByCoordinadorId(Long coordinadorId);

    /**
     * Lista usuarios por rol
     */
    List<Usuario> findByRol(Rol rol);

    /**
     * Lista usuarios activos
     */
    List<Usuario> findByEstadoTrue();

    /**
     * Verifica si existe un usuario con ese nombreUsuario
     */
    boolean existsByNombreUsuario(String nombreUsuario);

    /**
     * Busca usuarios por coordinador y estado
     */
    List<Usuario> findByCoordinadorIdAndEstado(Long coordinadorId, Boolean estado);
}
