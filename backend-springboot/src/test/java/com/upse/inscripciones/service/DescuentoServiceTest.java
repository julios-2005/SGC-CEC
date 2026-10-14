package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.DescuentoResponse;
import com.upse.inscripciones.entity.Descuento;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.DescuentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Reglas de los descuentos: validación del porcentaje, unicidad por nombre y
 * por tipo de participante, y que todo cambio recalcule los informes
 * económicos (un descuento afecta el ingreso de varias planificaciones).
 */
class DescuentoServiceTest {

    private DescuentoRepository repo;
    private InformeEconomicoService informes;
    private DescuentoService servicio;

    @BeforeEach
    void setup() {
        repo = mock(DescuentoRepository.class);
        informes = mock(InformeEconomicoService.class);
        servicio = new DescuentoService(repo, informes);
        when(repo.save(any(Descuento.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private static Descuento existente(String nombre, TipoUsuario tipo, String porcentaje, boolean activo) {
        return Descuento.builder().id(1L).nombre(nombre).tipoUsuario(tipo)
                .porcentaje(new BigDecimal(porcentaje)).estado(activo).build();
    }

    // ── registrar ────────────────────────────────────────────────────────────

    @Test
    void registrarNormalizaElNombreLoGuardaYRecalculaLosInformes() {
        DescuentoResponse r = servicio.registrar("  beca estudiantil ", TipoUsuario.ESTUDIANTE_UPSE, new BigDecimal("50"));

        assertThat(r.getNombre()).isEqualTo("BECA ESTUDIANTIL");
        assertThat(r.getTipoUsuario()).isEqualTo(TipoUsuario.ESTUDIANTE_UPSE);
        assertThat(r.getTipoUsuarioDescripcion()).isEqualTo("Estudiante Upse");
        assertThat(r.getPorcentaje()).isEqualByComparingTo("50");
        assertThat(r.getEstado()).isTrue();
        verify(repo).existsByNombre("BECA ESTUDIANTIL");
        verify(informes).recalcularTodo();
    }

    @Test
    void elPorcentajeAdmiteLosExtremosCeroYCien() {
        assertThat(servicio.registrar("CERO", null, BigDecimal.ZERO).getPorcentaje()).isEqualByComparingTo("0");
        assertThat(servicio.registrar("CIEN", null, new BigDecimal("100")).getPorcentaje()).isEqualByComparingTo("100");
    }

    @Test
    void registrarRechazaNombreOPorcentajeInvalidosSinGuardarNiRecalcular() {
        assertThatThrownBy(() -> servicio.registrar(null, null, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("nombre");
        assertThatThrownBy(() -> servicio.registrar("   ", null, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("nombre");
        assertThatThrownBy(() -> servicio.registrar("BECA", null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("porcentaje es obligatorio");
        assertThatThrownBy(() -> servicio.registrar("BECA", null, new BigDecimal("-0.01")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("entre 0 y 100");
        assertThatThrownBy(() -> servicio.registrar("BECA", null, new BigDecimal("100.01")))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("entre 0 y 100");

        verify(repo, never()).save(any(Descuento.class));
        verifyNoInteractions(informes);
    }

    @Test
    void registrarRechazaUnNombreYaExistente() {
        when(repo.existsByNombre("BECA")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("beca", null, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un descuento con el nombre");

        verify(repo, never()).save(any(Descuento.class));
        verifyNoInteractions(informes);
    }

    @Test
    void soloPuedeHaberUnDescuentoPorTipoDeParticipante() {
        when(repo.existsByTipoUsuario(TipoUsuario.DOCENTE_UPSE)).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar("DOCENTES", TipoUsuario.DOCENTE_UPSE, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Docente Upse");

        verify(repo, never()).save(any(Descuento.class));
    }

    @Test
    void unDescuentoSinTipoDeParticipanteNoConsultaLaUnicidadPorTipo() {
        servicio.registrar("GENERAL", null, BigDecimal.TEN);

        verify(repo, never()).existsByTipoUsuario(any(TipoUsuario.class));
    }

    // ── actualizar ───────────────────────────────────────────────────────────

    @Test
    void actualizarConElMismoNombreNoLoTomaPorDuplicado() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente("BECA", null, "10", true)));

        DescuentoResponse r = servicio.actualizar(1L, " beca ", null, new BigDecimal("20"));

        assertThat(r.getPorcentaje()).isEqualByComparingTo("20");
        verify(repo, never()).existsByNombre(anyString());
        verify(informes).recalcularTodo();
    }

    @Test
    void actualizarRechazaTomarElNombreDeOtroDescuento() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente("BECA", null, "10", true)));
        when(repo.existsByNombre("OTRO")).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, "otro", null, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe otro descuento con el nombre");

        verify(repo, never()).save(any(Descuento.class));
        verifyNoInteractions(informes);
    }

    @Test
    void actualizarRechazaMoverseAUnTipoQueYaTieneDescuento() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente("BECA", TipoUsuario.MAESTRANDO, "10", true)));
        when(repo.existsByTipoUsuario(TipoUsuario.GRADUADO)).thenReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(1L, "BECA", TipoUsuario.GRADUADO, BigDecimal.TEN))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe otro descuento para el tipo");

        verify(repo, never()).save(any(Descuento.class));
    }

    @Test
    void actualizarConservandoSuTipoNoLoTomaPorDuplicado() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente("BECA", TipoUsuario.MAESTRANDO, "10", true)));

        servicio.actualizar(1L, "BECA", TipoUsuario.MAESTRANDO, new BigDecimal("15"));

        verify(repo, never()).existsByTipoUsuario(any(TipoUsuario.class));
    }

    @Test
    void actualizarUnDescuentoInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(9L, "BECA", null, BigDecimal.TEN))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── estado y eliminación ─────────────────────────────────────────────────

    @Test
    void cambiarElEstadoRecalculaPorqueCambiaElPorcentajeEfectivo() {
        Descuento d = existente("BECA", null, "10", true);
        when(repo.findById(1L)).thenReturn(Optional.of(d));

        DescuentoResponse r = servicio.cambiarEstado(1L, false);

        assertThat(r.getEstado()).isFalse();
        assertThat(d.getEstado()).isFalse();
        verify(informes).recalcularTodo();
    }

    @Test
    void eliminarBorraYRecalcula() {
        when(repo.existsById(1L)).thenReturn(true);

        servicio.eliminar(1L);

        verify(repo).deleteById(1L);
        verify(informes).recalcularTodo();
    }

    @Test
    void eliminarUnDescuentoInexistenteNoBorraNiRecalcula() {
        when(repo.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> servicio.eliminar(9L)).isInstanceOf(RecursoNoEncontradoException.class);

        verify(repo, never()).deleteById(anyLong());
        verifyNoInteractions(informes);
    }

    // ── consultas ────────────────────────────────────────────────────────────

    @Test
    void elPorcentajeAplicableSoloCuentaSiElDescuentoEstaActivo() {
        when(repo.findByTipoUsuario(TipoUsuario.ESTUDIANTE_UPSE))
                .thenReturn(Optional.of(existente("BECA", TipoUsuario.ESTUDIANTE_UPSE, "30", true)));
        when(repo.findByTipoUsuario(TipoUsuario.DOCENTE_UPSE))
                .thenReturn(Optional.of(existente("DOC", TipoUsuario.DOCENTE_UPSE, "40", false)));
        when(repo.findByTipoUsuario(TipoUsuario.GRADUADO)).thenReturn(Optional.empty());

        assertThat(servicio.obtenerPorcentajePara(TipoUsuario.ESTUDIANTE_UPSE)).isEqualByComparingTo("30");
        assertThat(servicio.obtenerPorcentajePara(TipoUsuario.DOCENTE_UPSE)).isEqualByComparingTo("0");
        assertThat(servicio.obtenerPorcentajePara(TipoUsuario.GRADUADO)).isEqualByComparingTo("0");
    }

    @Test
    void listarActivosDevuelveSoloLosQueEntregaElRepositorio() {
        when(repo.findByEstadoTrue()).thenReturn(List.of(existente("BECA", null, "10", true)));

        List<DescuentoResponse> activos = servicio.listarActivos();

        assertThat(activos).extracting(DescuentoResponse::getNombre).containsExactly("BECA");
    }

    @Test
    void obtenerPorIdInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPorId(3L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
