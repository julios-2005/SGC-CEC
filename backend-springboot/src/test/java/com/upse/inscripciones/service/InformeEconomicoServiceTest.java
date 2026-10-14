package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.EgresoRequest;
import com.upse.inscripciones.dto.IngresoRequest;
import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.InformeEconomicoRepository;
import com.upse.inscripciones.repository.InscripcionRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cubre InformeEconomicoService#recalcularYGuardar (el cálculo que alimenta
 * todo el Informe Económico) y las ediciones manuales de ingresos/egresos.
 * Es la lógica de negocio con más impacto financiero directo del sistema, y
 * hasta ahora no tenía ningún test.
 */
class InformeEconomicoServiceTest {

    private PlanificacionRepository planificaciones;
    private InscripcionRepository inscripciones;
    private InformeEconomicoRepository informes;
    private DescuentoService descuentos;
    private InformeEconomicoService service;

    private void construirService() {
        planificaciones = mock(PlanificacionRepository.class);
        inscripciones = mock(InscripcionRepository.class);
        informes = mock(InformeEconomicoRepository.class);
        descuentos = mock(DescuentoService.class);
        service = new InformeEconomicoService(planificaciones, inscripciones, informes, descuentos);
    }

    private Curso cursoDePrueba(String costo) {
        return Curso.builder().idCurso(1L).nombre("Curso de prueba")
                .costo(new BigDecimal(costo)).modalidad(Modalidad.VIRTUAL).build();
    }

    private Coordinador coordinadorDePrueba() {
        return Coordinador.builder().id(1L).nombres("Ana").apellidos("Pérez").build();
    }

    private Especialista especialistaDePrueba(Long id, String nombre, String pais) {
        return Especialista.builder().id(id).nombres(nombre).apellidos("Apellido")
                .paisNacionalidad(pais).build();
    }

    private Planificacion planificacionDePrueba(BigDecimal costoEspecialista, Curso curso) {
        return Planificacion.builder().id(2L).curso(curso).coordinador(coordinadorDePrueba())
                .especialista(especialistaDePrueba(10L, "Docente Único", "EC"))
                .fechaInicio(LocalDate.of(2026, 3, 1)).fechaFin(LocalDate.of(2026, 3, 30))
                .costoEspecialista(costoEspecialista).build();
    }

    private Inscripcion aceptada(TipoUsuario tipo, Planificacion planificacion) {
        return Inscripcion.builder().tipoUsuario(tipo).planificacion(planificacion)
                .estado(EstadoInscripcion.ACEPTADA).build();
    }

    // Simula el comportamiento real: guarda y devuelve la misma entidad
    // (facilita luego encadenar obtenerDetalle sin duplicar fixtures).
    private void permitirGuardarYReleer(InformeEconomico informe, Planificacion planificacion) {
        when(informes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(informes.findByPlanificacionId(planificacion.getId())).thenReturn(Optional.of(informe));
        when(informes.findAllConDetalles()).thenReturn(List.of(informe));
        when(planificaciones.findByIdForUpdate(planificacion.getId())).thenReturn(Optional.of(planificacion));
    }

    // ── recalcularYGuardar ───────────────────────────────────────────────

    @Test
    void calculaIngresosPorTipoDeParticipanteAplicandoElDescuentoVigente() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(new BigDecimal("200.00"), curso);

        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.empty());
        when(inscripciones.findAceptadasByPlanificacionId(2L)).thenReturn(List.of(
                aceptada(TipoUsuario.EXTERNO, planificacion),
                aceptada(TipoUsuario.ESTUDIANTE_UPSE, planificacion)));
        when(descuentos.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        when(descuentos.obtenerPorcentajePara(TipoUsuario.ESTUDIANTE_UPSE)).thenReturn(new BigDecimal("10"));

        service.recalcularYGuardar(2L);

        var captor = ArgumentCaptor.forClass(InformeEconomico.class);
        verify(informes).saveAndFlush(captor.capture());
        InformeEconomico guardado = captor.getValue();

        // Sin descuento: 100.00 completos. Con 10% de descuento: 90.00.
        assertThat(guardado.getIngresosTotal()).isEqualByComparingTo("190.00");
        assertThat(guardado.getParticipantes()).isEqualTo(2);
        // Una línea por cada valor de TipoUsuario, no solo por los que tienen inscritos.
        assertThat(guardado.getLineasIngreso()).hasSize(TipoUsuario.values().length);
        // Sin docentes/honorarios registrados en la planificación: se usa costoEspecialista tal cual.
        assertThat(guardado.getEgresosTotal()).isEqualByComparingTo("200.00");
        assertThat(guardado.getUtilidad()).isEqualByComparingTo("-10.00");
    }

    @Test
    void respetaLaCantidadEditadaManualmenteAlRecalcular() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(new BigDecimal("50.00"), curso);

        InformeEconomicoLineaIngreso lineaManual = InformeEconomicoLineaIngreso.builder()
                .tipoUsuario(TipoUsuario.EXTERNO).cantidad(5).cantidadManual(true)
                .valorUnitario(new BigDecimal("100.00")).total(new BigDecimal("500.00")).build();
        InformeEconomico existente = InformeEconomico.builder().planificacion(planificacion)
                .lineasIngreso(new ArrayList<>(List.of(lineaManual)))
                .lineasEgreso(new ArrayList<>()).build();

        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.of(existente));
        // Solo hay 1 inscripción aceptada real, pero la línea manual dice 5:
        // debe prevalecer la cantidad manual, no el conteo real.
        when(inscripciones.findAceptadasByPlanificacionId(2L)).thenReturn(List.of(aceptada(TipoUsuario.EXTERNO, planificacion)));
        when(descuentos.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        when(informes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recalcularYGuardar(2L);

        InformeEconomicoLineaIngreso lineaExterno = existente.getLineasIngreso().stream()
                .filter(l -> l.getTipoUsuario() == TipoUsuario.EXTERNO).findFirst().orElseThrow();
        assertThat(lineaExterno.getCantidad()).isEqualTo(5);
        assertThat(lineaExterno.isCantidadManual()).isTrue();
        assertThat(existente.getParticipantes()).isEqualTo(5);
        assertThat(existente.getIngresosTotal()).isEqualByComparingTo("500.00");
    }

    @Test
    void unInformeHistoricoNoRecalculaIngresosPeroSiRecalculaElEgreso() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(new BigDecimal("300.00"), curso);
        InformeEconomico historico = InformeEconomico.builder().planificacion(planificacion)
                .historico(true).ingresosTotal(new BigDecimal("9999.00")).participantes(42)
                .lineasIngreso(new ArrayList<>()).lineasEgreso(new ArrayList<>()).build();

        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.of(historico));
        when(informes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recalcularYGuardar(2L);

        // Las cifras históricas de ingresos no se tocan...
        assertThat(historico.getIngresosTotal()).isEqualByComparingTo("9999.00");
        assertThat(historico.getParticipantes()).isEqualTo(42);
        // ...pero el egreso SÍ, porque depende del costoEspecialista vivo de la Planificación.
        assertThat(historico.getEgresosTotal()).isEqualByComparingTo("300.00");
        verifyNoInteractions(inscripciones);
    }

    @Test
    void reparteHonorariosEIncluyeLaTransferenciaDelEspecialistaExtranjero() {
        construirService();
        Curso curso = cursoDePrueba("50.00");
        Especialista local = especialistaDePrueba(10L, "Local", "EC");
        Especialista extranjero = especialistaDePrueba(11L, "Extranjero", "CO");
        Planificacion planificacion = Planificacion.builder().id(2L).curso(curso)
                .coordinador(coordinadorDePrueba())
                .especialista(local)
                .docentes(new ArrayList<>(List.of(local, extranjero)))
                .honorarios(new java.util.LinkedHashMap<>(java.util.Map.of(10L, new BigDecimal("100.00"), 11L, new BigDecimal("100.00"))))
                .fechaInicio(LocalDate.of(2026, 3, 1)).fechaFin(LocalDate.of(2026, 3, 30)).build();

        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.empty());
        when(inscripciones.findAceptadasByPlanificacionId(2L)).thenReturn(List.of());
        when(descuentos.obtenerPorcentajePara(any())).thenReturn(BigDecimal.ZERO);
        when(informes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recalcularYGuardar(2L);

        var captor = ArgumentCaptor.forClass(InformeEconomico.class);
        verify(informes).saveAndFlush(captor.capture());
        InformeEconomico guardado = captor.getValue();

        // Local: 100 sin transferencia. Extranjero: 100 + 25% = 125. Total: 225.
        assertThat(guardado.getEgresosTotal()).isEqualByComparingTo("225.00");
        assertThat(guardado.getLineasEgreso()).hasSize(3); // 2 honorarios + 1 transferencia
        assertThat(guardado.getLineasEgreso().stream().map(InformeEconomicoLineaEgreso::getConcepto))
                .anyMatch(c -> c.contains("Costo de transferencia"));
    }

    // ── guardarIngreso ───────────────────────────────────────────────────

    @Test
    void guardarIngresoAjustaSoloLaDiferenciaEnElTotalDelInforme() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(new BigDecimal("0.00"), curso);
        InformeEconomicoLineaIngreso linea = InformeEconomicoLineaIngreso.builder().id(7L)
                .tipoUsuario(TipoUsuario.EXTERNO).cantidad(2).cantidadManual(false)
                .valorUnitario(new BigDecimal("100.00")).total(new BigDecimal("200.00")).build();
        InformeEconomico informe = InformeEconomico.builder().id(9L).planificacion(planificacion)
                .cifrasPendientes(false).participantes(2).ingresosTotal(new BigDecimal("200.00"))
                .egresosTotal(BigDecimal.ZERO)
                .lineasIngreso(new ArrayList<>(List.of(linea))).lineasEgreso(new ArrayList<>()).build();
        permitirGuardarYReleer(informe, planificacion);

        service.guardarIngreso(2L, 7L, new IngresoRequest(5));

        assertThat(linea.getCantidad()).isEqualTo(5);
        assertThat(linea.isCantidadManual()).isTrue();
        assertThat(linea.getTotal()).isEqualByComparingTo("500.00");
        // 200 (otras líneas, aquí ninguna más) + delta (500-200) = 500
        assertThat(informe.getIngresosTotal()).isEqualByComparingTo("500.00");
        assertThat(informe.getParticipantes()).isEqualTo(5);
    }

    @Test
    void noPermiteEditarIngresosDeUnaReferenciaConCifrasPendientes() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(BigDecimal.ZERO, curso);
        InformeEconomico informe = InformeEconomico.builder().id(9L).planificacion(planificacion)
                .cifrasPendientes(true).lineasIngreso(new ArrayList<>()).lineasEgreso(new ArrayList<>()).build();
        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.of(informe));

        assertThatThrownBy(() -> service.guardarIngreso(2L, 7L, new IngresoRequest(5)))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("cifras informadas");
    }

    @Test
    void noPermiteSuperarElMaximoPermitidoAlEditarUnIngreso() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(BigDecimal.ZERO, curso);
        InformeEconomicoLineaIngreso linea = InformeEconomicoLineaIngreso.builder().id(7L)
                .tipoUsuario(TipoUsuario.EXTERNO).cantidad(1).valorUnitario(new BigDecimal("1000000.00"))
                .total(new BigDecimal("1000000.00")).build();
        InformeEconomico informe = InformeEconomico.builder().id(9L).planificacion(planificacion)
                .cifrasPendientes(false).ingresosTotal(new BigDecimal("1000000.00")).egresosTotal(BigDecimal.ZERO)
                .lineasIngreso(new ArrayList<>(List.of(linea))).lineasEgreso(new ArrayList<>()).build();
        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.of(informe));

        assertThatThrownBy(() -> service.guardarIngreso(2L, 7L, new IngresoRequest(100000)))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("máximo permitido");
    }

    // ── guardarEgreso / eliminarEgreso ───────────────────────────────────

    @Test
    void agregarUnEgresoManualSumaAlTotalYALaUtilidad() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(BigDecimal.ZERO, curso);
        InformeEconomico informe = InformeEconomico.builder().id(9L).planificacion(planificacion)
                .cifrasPendientes(false).ingresosTotal(new BigDecimal("500.00")).egresosTotal(new BigDecimal("100.00"))
                .lineasIngreso(new ArrayList<>()).lineasEgreso(new ArrayList<>()).build();
        permitirGuardarYReleer(informe, planificacion);

        service.guardarEgreso(2L, null, new EgresoRequest("Refrigerios", 10, new BigDecimal("5.00")));

        assertThat(informe.getEgresosTotal()).isEqualByComparingTo("150.00");
        assertThat(informe.getUtilidad()).isEqualByComparingTo("350.00");
        assertThat(informe.getLineasEgreso()).hasSize(1);
        assertThat(informe.getLineasEgreso().get(0).isManual()).isTrue();
    }

    @Test
    void noPermiteEditarUnaLineaDeEgresoQueNoEsManual() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(BigDecimal.ZERO, curso);
        InformeEconomicoLineaEgreso automatica = InformeEconomicoLineaEgreso.builder().id(3L)
                .concepto("Especialistas (honorario total)").manual(false)
                .cantidad(1).valorUnitario(new BigDecimal("200.00")).total(new BigDecimal("200.00")).build();
        InformeEconomico informe = InformeEconomico.builder().id(9L).planificacion(planificacion)
                .cifrasPendientes(false).ingresosTotal(BigDecimal.ZERO).egresosTotal(new BigDecimal("200.00"))
                .lineasIngreso(new ArrayList<>()).lineasEgreso(new ArrayList<>(List.of(automatica))).build();
        when(planificaciones.findByIdForUpdate(2L)).thenReturn(Optional.of(planificacion));
        when(informes.findByPlanificacionId(2L)).thenReturn(Optional.of(informe));

        assertThatThrownBy(() -> service.eliminarEgreso(2L, 3L))
                .isInstanceOf(ValidacionException.class)
                .hasMessageContaining("histórica o automática");
    }

    @Test
    void eliminarUnEgresoManualRestaSuTotalYRecalculaLaUtilidad() {
        construirService();
        Curso curso = cursoDePrueba("100.00");
        Planificacion planificacion = planificacionDePrueba(BigDecimal.ZERO, curso);
        InformeEconomicoLineaEgreso manual = InformeEconomicoLineaEgreso.builder().id(4L)
                .concepto("Refrigerios").manual(true)
                .cantidad(2).valorUnitario(new BigDecimal("25.00")).total(new BigDecimal("50.00")).build();
        InformeEconomico informe = InformeEconomico.builder().id(9L).planificacion(planificacion)
                .cifrasPendientes(false).ingresosTotal(new BigDecimal("500.00")).egresosTotal(new BigDecimal("50.00"))
                .lineasIngreso(new ArrayList<>()).lineasEgreso(new ArrayList<>(List.of(manual))).build();
        permitirGuardarYReleer(informe, planificacion);

        service.eliminarEgreso(2L, 4L);

        assertThat(informe.getLineasEgreso()).isEmpty();
        assertThat(informe.getEgresosTotal()).isEqualByComparingTo("0.00");
        assertThat(informe.getUtilidad()).isEqualByComparingTo("500.00");
    }
}
