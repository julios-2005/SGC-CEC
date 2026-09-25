package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.entity.Planificacion;
import com.upse.inscripciones.exception.ValidacionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regla del CEC: la transferencia internacional (25%) es un costo ADICIONAL
 * que asume el CEC; el especialista extranjero recibe su honorario completo.
 * Estas pruebas son puras (sin base de datos): fijan el cálculo y la
 * validación de honorarios individuales.
 */
class HonorariosEspecialistasTest {

    private static Especialista docente(long id, String pais) {
        return Especialista.builder().id(id).nombres("Docente" + id).apellidos("Prueba").paisNacionalidad(pais).build();
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    // ── esExtranjero ─────────────────────────────────────────────────────────

    @Test
    void soloEsExtranjeroQuienTieneUnPaisDistintoDeEcuador() {
        assertThat(HonorariosEspecialistas.esExtranjero(docente(1, null))).isFalse();
        assertThat(HonorariosEspecialistas.esExtranjero(docente(1, ""))).isFalse();
        assertThat(HonorariosEspecialistas.esExtranjero(docente(1, "   "))).isFalse();
        assertThat(HonorariosEspecialistas.esExtranjero(docente(1, "EC"))).isFalse();
        assertThat(HonorariosEspecialistas.esExtranjero(docente(1, " ec "))).isFalse();
        assertThat(HonorariosEspecialistas.esExtranjero(docente(1, "CO"))).isTrue();
    }

    // ── desglosar ────────────────────────────────────────────────────────────

    @Test
    void unDocenteNacionalCuestaSoloSuHonorario() {
        Planificacion p = Planificacion.builder().docentes(List.of(docente(1, "EC")))
                .costoEspecialista(bd("1000")).build();

        var pago = HonorariosEspecialistas.desglosar(p).get(0);

        assertThat(pago.honorario()).isEqualByComparingTo("1000.00");
        assertThat(pago.costoTransferencia()).isEqualByComparingTo("0.00");
        assertThat(pago.pagoNeto()).isEqualByComparingTo("1000.00");
    }

    @Test
    void unDocenteExtranjeroRecibeSuHonorarioCompletoYLaTransferenciaLaAsumeElCec() {
        Planificacion p = Planificacion.builder().docentes(List.of(docente(1, "CO")))
                .costoEspecialista(bd("1000")).build();

        var pago = HonorariosEspecialistas.desglosar(p).get(0);

        assertThat(pago.honorario()).isEqualByComparingTo("1000.00");
        assertThat(pago.costoTransferencia()).isEqualByComparingTo("250.00");
        assertThat(pago.pagoNeto()).isEqualByComparingTo("1250.00");
    }

    @Test
    void variosDocentesSeCalculanPorSeparadoYConRedondeoHaciaArribaEnLaMitad() {
        Map<Long, BigDecimal> honorarios = new LinkedHashMap<>();
        honorarios.put(1L, bd("1000.03"));
        honorarios.put(2L, bd("123.45"));
        honorarios.put(3L, bd("50"));
        Planificacion p = Planificacion.builder()
                .docentes(List.of(docente(1, "CO"), docente(2, "PE"), docente(3, null)))
                .honorarios(honorarios).build();

        var pagos = HonorariosEspecialistas.desglosar(p);

        assertThat(pagos).hasSize(3);
        // 1000.03 * 0.25 = 250.0075 -> 250.01
        assertThat(pagos.get(0).costoTransferencia()).isEqualByComparingTo("250.01");
        assertThat(pagos.get(0).pagoNeto()).isEqualByComparingTo("1250.04");
        // 123.45 * 0.25 = 30.8625 -> 30.86
        assertThat(pagos.get(1).costoTransferencia()).isEqualByComparingTo("30.86");
        assertThat(pagos.get(1).pagoNeto()).isEqualByComparingTo("154.31");
        // Sin nacionalidad indicada no se considera extranjero.
        assertThat(pagos.get(2).costoTransferencia()).isEqualByComparingTo("0.00");
        assertThat(pagos.get(2).pagoNeto()).isEqualByComparingTo("50.00");
    }

    @Test
    void conVariosDocentesNoSeAdivinaElRepartoDeUnTotal() {
        Planificacion sinHonorarios = Planificacion.builder()
                .docentes(List.of(docente(1, "CO"), docente(2, "EC")))
                .costoEspecialista(bd("1000")).build();
        Planificacion incompleto = Planificacion.builder()
                .docentes(List.of(docente(1, "CO"), docente(2, "EC")))
                .honorarios(Map.of(1L, bd("500"))).build();

        assertThat(HonorariosEspecialistas.desglosar(sinHonorarios)).isEmpty();
        assertThat(HonorariosEspecialistas.desglosar(incompleto)).isEmpty();
    }

    @Test
    void sinHonorarioNiCostoDelEspecialistaElPagoEsCero() {
        Planificacion p = Planificacion.builder().docentes(List.of(docente(1, "CO"))).build();

        var pago = HonorariosEspecialistas.desglosar(p).get(0);

        assertThat(pago.honorario()).isEqualByComparingTo("0.00");
        assertThat(pago.pagoNeto()).isEqualByComparingTo("0.00");
    }

    // ── validar ──────────────────────────────────────────────────────────────

    @Test
    void conUnSoloDocenteSinDesgloseElTotalEsSuHonorario() {
        Map<Long, BigDecimal> r = HonorariosEspecialistas.validar(List.of(docente(1, "EC")), null, bd("500"), null);

        assertThat(r).containsOnlyKeys(1L);
        assertThat(r.get(1L)).isEqualByComparingTo("500");
        assertThat(HonorariosEspecialistas.validar(List.of(docente(1, "EC")), null, null, null).get(1L))
                .isEqualByComparingTo("0");
    }

    @Test
    void variosDocentesNacionalesSinDesgloseNoGeneranHonorarios() {
        var r = HonorariosEspecialistas.validar(List.of(docente(1, "EC"), docente(2, null)), null, bd("500"), null);

        assertThat(r).isEmpty();
    }

    @Test
    void variosDocentesConUnExtranjeroExigenElHonorarioIndividual() {
        assertThatThrownBy(() -> HonorariosEspecialistas.validar(
                List.of(docente(1, "EC"), docente(2, "CO")), null, bd("500"), null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("honorario individual");
    }

    @Test
    void sinNuevosImportesSeConservanLosAnterioresSiElTotalNoCambio() {
        Map<Long, BigDecimal> anteriores = new LinkedHashMap<>();
        anteriores.put(1L, bd("300.00"));
        anteriores.put(2L, bd("200.00"));

        var r = HonorariosEspecialistas.validar(List.of(docente(1, "CO"), docente(2, "EC")), null, bd("500"), anteriores);

        assertThat(r).containsOnlyKeys(1L, 2L);
        assertThat(r.get(1L)).isEqualByComparingTo("300");
    }

    @Test
    void elNumeroDeImportesDebeIgualarAlDeDocentes() {
        assertThatThrownBy(() -> HonorariosEspecialistas.validar(
                List.of(docente(1, "EC"), docente(2, "EC")), List.of(bd("100")), null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("un honorario por cada especialista");
    }

    @Test
    void cadaImporteDebeSerNoNegativoConHastaDosDecimalesYDentroDelMaximo() {
        List<Especialista> uno = List.of(docente(1, "EC"));
        for (BigDecimal invalido : new BigDecimal[] {bd("-1"), bd("10.005"), bd("100000000.00")}) {
            assertThatThrownBy(() -> HonorariosEspecialistas.validar(uno, List.of(invalido), null, null))
                    .isInstanceOf(ValidacionException.class).hasMessageContaining("no negativo");
        }
        assertThatThrownBy(() -> HonorariosEspecialistas.validar(uno, java.util.Collections.singletonList(null), null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("no negativo");
    }

    @Test
    void laSumaDeLosHonorariosNoPuedeSuperarElMaximo() {
        assertThatThrownBy(() -> HonorariosEspecialistas.validar(
                List.of(docente(1, "EC"), docente(2, "EC")), List.of(bd("99999999.99"), bd("1")), null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("supera el máximo");
    }

    @Test
    void elTotalInformadoDebeCoincidirConLaSumaDeLosHonorarios() {
        List<Especialista> dos = List.of(docente(1, "EC"), docente(2, "CO"));

        assertThatThrownBy(() -> HonorariosEspecialistas.validar(dos, List.of(bd("100"), bd("50")), bd("200"), null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("debe coincidir");

        var r = HonorariosEspecialistas.validar(dos, List.of(bd("100"), bd("50.5")), bd("150.50"), null);

        assertThat(r).containsOnlyKeys(1L, 2L);
        assertThat(r.get(1L)).isEqualByComparingTo("100.00");
        assertThat(r.get(2L)).isEqualByComparingTo("50.50");
    }
}
