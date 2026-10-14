package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.ConsolidadoResponse;
import com.upse.inscripciones.dto.GastoCecRequest;
import com.upse.inscripciones.dto.InformeEconomicoResumenResponse;
import com.upse.inscripciones.entity.GastoCec;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.GastoCecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GastoCecService cruza el gasto de personal CEC (un valor por año, con
 * control de versión optimista) con el resumen de todos los informes
 * económicos de ese año para calcular la utilidad neta ("utilidad CEC" =
 * utilidad total − gasto de personal). Estas pruebas fijan ese cálculo, la
 * validación de rango de año, el valor por defecto cuando el año no tiene
 * gasto registrado todavía, y el chequeo de versión al guardar.
 */
class GastoCecServiceTest {

    private GastoCecRepository repo;
    private InformeEconomicoService informes;
    private GastoCecService servicio;

    @BeforeEach
    void setup() {
        repo = mock(GastoCecRepository.class);
        informes = mock(InformeEconomicoService.class);
        servicio = new GastoCecService(repo, informes);
    }

    private static InformeEconomicoResumenResponse resumen(int anio, int participantes,
                                                            BigDecimal ingresos, BigDecimal egresos) {
        return InformeEconomicoResumenResponse.builder()
                .anio(anio).fechaInicio(LocalDate.of(anio, 1, 1))
                .participantes(participantes).ingresos(ingresos).egresos(egresos).build();
    }

    // ── obtener ──────────────────────────────────────────────────────────────

    @Test
    void unAnioFueraDeRangoSeRechazaSinConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.obtener(1999))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("entre 2000 y 2100");
        assertThatThrownBy(() -> servicio.obtener(2101))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("entre 2000 y 2100");

        verify(repo, never()).findById(any());
    }

    @Test
    void unAnioSinGastoRegistradoUsaImporteCeroYVersionMenosUno() {
        when(repo.findById(2026)).thenReturn(Optional.empty());
        when(informes.listarResumen()).thenReturn(List.of());

        ConsolidadoResponse r = servicio.obtener(2026);

        assertThat(r.gastosPersonal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.version()).isEqualTo(-1L);
    }

    @Test
    void sumaLosIngresosYEgresosSoloDeLosInformesDelAnioSolicitado() {
        GastoCec gasto = new GastoCec();
        gasto.setAnio(2026);
        gasto.setImporte(new BigDecimal("500.00"));
        gasto.setVersion(3L);
        when(repo.findById(2026)).thenReturn(Optional.of(gasto));
        when(informes.listarResumen()).thenReturn(List.of(
                resumen(2026, 10, new BigDecimal("1000"), new BigDecimal("400")),
                resumen(2025, 5, new BigDecimal("999"), new BigDecimal("999"))));

        ConsolidadoResponse r = servicio.obtener(2026);

        assertThat(r.participantes()).isEqualTo(10);
        assertThat(r.ingresos()).isEqualByComparingTo("1000");
        assertThat(r.egresos()).isEqualByComparingTo("400");
        assertThat(r.utilidad()).isEqualByComparingTo("600");
        assertThat(r.gastosPersonal()).isEqualByComparingTo("500.00");
        assertThat(r.utilidadCec()).isEqualByComparingTo("100.00");
        assertThat(r.version()).isEqualTo(3L);
    }

    @Test
    void unInformeDe2026MarcadoComoExcluidoNoSeSumaAlConsolidado() {
        when(repo.findById(2026)).thenReturn(Optional.empty());
        InformeEconomicoResumenResponse excluido = InformeEconomicoResumenResponse.builder()
                .anio(2026).fechaInicio(LocalDate.of(2026, 1, 1)).excluido2026(true)
                .participantes(99).ingresos(new BigDecimal("9999")).egresos(BigDecimal.ZERO).build();
        when(informes.listarResumen()).thenReturn(List.of(excluido));

        ConsolidadoResponse r = servicio.obtener(2026);

        assertThat(r.participantes()).isZero();
        assertThat(r.ingresos()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── actualizar ───────────────────────────────────────────────────────────

    @Test
    void actualizarUnAnioSinGastoPrevioLoCreaConVersionMenosUno() {
        when(repo.findById(2026)).thenReturn(Optional.empty());
        when(informes.listarResumen()).thenReturn(List.of());

        servicio.actualizar(2026, new GastoCecRequest(new BigDecimal("300.00"), -1L));

        var captor = org.mockito.ArgumentCaptor.forClass(GastoCec.class);
        verify(repo).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAnio()).isEqualTo(2026);
        assertThat(captor.getValue().getImporte()).isEqualByComparingTo("300.00");
    }

    @Test
    void actualizarConLaVersionCorrectaGuardaElNuevoImporte() {
        GastoCec gasto = new GastoCec();
        gasto.setAnio(2026);
        gasto.setImporte(new BigDecimal("100.00"));
        gasto.setVersion(2L);
        when(repo.findById(2026)).thenReturn(Optional.of(gasto));
        when(informes.listarResumen()).thenReturn(List.of());

        servicio.actualizar(2026, new GastoCecRequest(new BigDecimal("450.00"), 2L));

        assertThat(gasto.getImporte()).isEqualByComparingTo("450.00");
        verify(repo).saveAndFlush(gasto);
    }

    @Test
    void actualizarConUnaVersionDesactualizadaSeRechazaSinGuardar() {
        GastoCec gasto = new GastoCec();
        gasto.setAnio(2026);
        gasto.setImporte(new BigDecimal("100.00"));
        gasto.setVersion(2L);
        when(repo.findById(2026)).thenReturn(Optional.of(gasto));

        assertThatThrownBy(() -> servicio.actualizar(2026, new GastoCecRequest(new BigDecimal("450.00"), 1L)))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("modificados por otra persona");

        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void actualizarUnAnioFueraDeRangoSeRechazaSinConsultarElRepositorio() {
        assertThatThrownBy(() -> servicio.actualizar(1999, new GastoCecRequest(BigDecimal.TEN, -1L)))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("entre 2000 y 2100");

        verify(repo, never()).findById(any());
    }
}
