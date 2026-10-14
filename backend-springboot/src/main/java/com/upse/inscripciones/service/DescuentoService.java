package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.DescuentoResponse;
import com.upse.inscripciones.entity.Descuento;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.DescuentoRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DescuentoService {

    private final DescuentoRepository descuentoRepository;

    // InformeEconomicoService también depende de DescuentoService
    // (obtenerPorcentajePara), así que esta referencia se inyecta perezosa
    // (@Lazy en el PARÁMETRO del constructor, no en el campo) para evitar
    // una dependencia circular entre los dos beans al arrancar Spring.
    private final InformeEconomicoService informeEconomicoService;

    public DescuentoService(DescuentoRepository descuentoRepository,
                             @Lazy InformeEconomicoService informeEconomicoService) {
        this.descuentoRepository = descuentoRepository;
        this.informeEconomicoService = informeEconomicoService;
    }

    // ── Listar ────────────────────────────────────────────────────────────────

    public List<DescuentoResponse> listarTodos() {
        return descuentoRepository.findAll()
                .stream()
                .map(DescuentoResponse::fromEntity)
                .toList();
    }

    public List<DescuentoResponse> listarActivos() {
        return descuentoRepository.findByEstadoTrue()
                .stream()
                .map(DescuentoResponse::fromEntity)
                .toList();
    }

    public DescuentoResponse obtenerPorId(Long id) {
        return DescuentoResponse.fromEntity(buscarPorId(id));
    }

    // ── Registrar ─────────────────────────────────────────────────────────────

    @Transactional
    public DescuentoResponse registrar(String nombre, TipoUsuario tipoUsuario, BigDecimal porcentaje) {
        validar(nombre, porcentaje);

        if (descuentoRepository.existsByNombre(nombre.trim().toUpperCase())) {
            throw new ValidacionException("Ya existe un descuento con el nombre \"" + nombre + "\".");
        }
        if (tipoUsuario != null && descuentoRepository.existsByTipoUsuario(tipoUsuario)) {
            throw new ValidacionException(
                    "Ya existe un descuento para el tipo de participante \"" + tipoUsuario.getDescripcion() + "\".");
        }

        Descuento descuento = Descuento.builder()
                .nombre(nombre.trim().toUpperCase())
                .tipoUsuario(tipoUsuario)
                .porcentaje(porcentaje)
                .build();

        Descuento guardado = descuentoRepository.save(descuento);

        // Un descuento nuevo puede afectar el ingreso de varias planificaciones a la vez.
        informeEconomicoService.recalcularTodo();

        return DescuentoResponse.fromEntity(guardado);
    }

    @Transactional
    public DescuentoResponse actualizar(Long id, String nombre, TipoUsuario tipoUsuario, BigDecimal porcentaje) {
        Descuento descuento = buscarPorId(id);
        validar(nombre, porcentaje);

        String nombreNuevo = nombre.trim().toUpperCase();
        if (!descuento.getNombre().equals(nombreNuevo)
                && descuentoRepository.existsByNombre(nombreNuevo)) {
            throw new ValidacionException("Ya existe otro descuento con el nombre \"" + nombre + "\".");
        }
        if (tipoUsuario != null && tipoUsuario != descuento.getTipoUsuario()
                && descuentoRepository.existsByTipoUsuario(tipoUsuario)) {
            throw new ValidacionException(
                    "Ya existe otro descuento para el tipo de participante \"" + tipoUsuario.getDescripcion() + "\".");
        }

        descuento.setNombre(nombreNuevo);
        descuento.setTipoUsuario(tipoUsuario);
        descuento.setPorcentaje(porcentaje);

        Descuento guardado = descuentoRepository.save(descuento);
        informeEconomicoService.recalcularTodo();

        return DescuentoResponse.fromEntity(guardado);
    }

    // ── Cambiar estado ────────────────────────────────────────────────────────

    @Transactional
    public DescuentoResponse cambiarEstado(Long id, boolean estado) {
        Descuento descuento = buscarPorId(id);
        descuento.setEstado(estado);
        Descuento guardado = descuentoRepository.save(descuento);

        // Activar/desactivar un descuento cambia el porcentaje efectivo (0% si
        // queda inactivo), hay que recalcular.
        informeEconomicoService.recalcularTodo();

        return DescuentoResponse.fromEntity(guardado);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!descuentoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("No se encontró el descuento con id " + id + ".");
        }
        descuentoRepository.deleteById(id);
        informeEconomicoService.recalcularTodo();
    }

    // ── Usado internamente por Inscripción / Informe Económico ─────────────────

    /** Porcentaje de descuento activo para un tipo de participante (0 si no hay ninguno configurado). */
    public BigDecimal obtenerPorcentajePara(TipoUsuario tipoUsuario) {
        return descuentoRepository.findByTipoUsuario(tipoUsuario)
                .filter(Descuento::getEstado)
                .map(Descuento::getPorcentaje)
                .orElse(BigDecimal.ZERO);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Descuento buscarPorId(Long id) {
        return descuentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el descuento con id " + id + "."));
    }

    private void validar(String nombre, BigDecimal porcentaje) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new ValidacionException("El nombre del descuento es obligatorio.");
        }
        if (porcentaje == null) {
            throw new ValidacionException("El porcentaje es obligatorio.");
        }
        if (porcentaje.compareTo(BigDecimal.ZERO) < 0
                || porcentaje.compareTo(new BigDecimal("100")) > 0) {
            throw new ValidacionException("El porcentaje debe estar entre 0 y 100.");
        }
    }
}
