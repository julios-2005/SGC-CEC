package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.*;
import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Informe económico: egresos manuales vs. automáticos, recálculo con descuentos, histórico, gasto anual del CEC y edición concurrente.
 */
@DataJpaTest(showSql = false, properties = {
    "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({CursoService.class, PlanificacionService.class, EspecialistaService.class, InformeEconomicoService.class,
        DescuentoService.class, DocentesService.class, GastoCecService.class})
class InformeEconomicoEgresosYGastosTest {
    @Autowired CursoService cursos;
    @Autowired PlanificacionService planificaciones;
    @Autowired InformeEconomicoService informes;
    @Autowired GastoCecService gastos;
    @Autowired PlanificacionRepository planRepo;
    @Autowired InformeEconomicoRepository informeRepo;
    @Autowired EspecialistaRepository especialistaRepo;
    @Autowired CoordinadorRepository coordinadorRepo;
    @Autowired InscripcionRepository inscripcionRepo;
    @Autowired DescuentoRepository descuentoRepo;
    @Autowired EntityManager em;
    @MockBean FileStorageService archivos;

    private List<Long> docentes(int cantidad) {
        List<Long> ids = new ArrayList<>();
        for (int n = 0; n < cantidad; n++) ids.add(especialistaRepo.save(Especialista.builder()
                .cedula("DOC" + UUID.randomUUID().toString().substring(0, 12)).nombres("Docente " + n)
                .apellidos("Prueba").estado(true).build()).getId());
        return ids;
    }

    private CursoRequest solicitud() {
        var r = new CursoRequest();
        r.setNombre("Diplomado de prueba"); r.setCodigo("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        r.setHoras(40); r.setCosto(new BigDecimal("100.00")); r.setCuposTotales(30);
        r.setModalidad("VIRTUAL"); r.setEstado("EN_ESPERA"); r.setAmbito(AmbitoCurso.INTERNACIONAL);
        return r;
    }

    // Los docentes se eligen al registrar la planificación (como en los HTML
    // originales), nunca en el curso: el curso solo guarda sus propios
    // datos. Por eso este helper ya recibe los docentes explícitamente en
    // vez de dejar que se copien de una lista propuesta en el curso.
    private PlanificacionResponse planificar(CursoResponse curso, List<Long> ids) {
        var c = coordinadorRepo.save(Coordinador.builder().cedula("0912345678").nombres("Ana")
                .apellidos("Prueba").estado(true).build());
        return planificaciones.guardarConDocentes(null, curso.getIdCurso(), c.getId(), ids.get(0),
                LocalDate.of(2026, 11, 1), "18:00", LocalDate.of(2026, 11, 30), Modalidad.VIRTUAL,
                new BigDecimal("200.00"), ids);
    }

    @Test
    void egresosManualesPersistenSeEditanSeEliminanYNoSeConfundenConAutomaticos() {
        var ids = docentes(1);
        var curso = cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA);
        var p = planificar(curso, ids);
        var detalle = informes.guardarEgreso(p.getId(), null, new EgresoRequest("Materiales", 2, new BigDecimal("12.50")));
        var manual = detalle.getEgresos().stream().filter(EgresoLineaResponse::isEditable).findFirst().orElseThrow();
        informes.recalcularTodo(); em.flush(); em.clear();
        assertThat(informes.obtenerDetalle(p.getId()).getEgresosTotal()).isEqualByComparingTo("225");
        detalle = informes.guardarEgreso(p.getId(), manual.getId(), new EgresoRequest("Materiales", 3, new BigDecimal("10.00")));
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("230");
        assertThat(detalle.getUtilidad()).isEqualByComparingTo("-230");
        detalle = informes.eliminarEgreso(p.getId(), manual.getId());
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("200");
    }

    @Test
    void noPermiteEditarFilaAutomaticaDesdeEgresosManuales() {
        var ids = docentes(1);
        var p = planificar(cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA), ids);
        em.flush();
        var automatica = informes.obtenerDetalle(p.getId()).getEgresos().get(0);
        assertThatThrownBy(() -> informes.guardarEgreso(p.getId(), automatica.getId(),
                new EgresoRequest("Alterar", 1, BigDecimal.ZERO))).hasMessageContaining("histórica o automática");
    }

    @Test
    void recalculoConDescuentosSumaSoloAceptadosYConservaLosGastosManuales() {
        var ids = docentes(1);
        var p = planificar(cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA), ids);
        descuentoRepo.save(Descuento.builder().nombre("UPSE").tipoUsuario(TipoUsuario.ESTUDIANTE_UPSE)
                .porcentaje(new BigDecimal("50")).estado(true).build());
        var plan = planRepo.findById(p.getId()).orElseThrow();
        for (var tipo : TipoUsuario.values()) {
            inscripcionRepo.save(Inscripcion.builder().planificacion(plan).tipoUsuario(tipo)
                    .nombreCompleto("Estudiante de prueba").cedula("EST" + tipo.ordinal())
                    .telefono("+593991234567").correoElectronico("prueba@example.test").direccion("Dirección")
                    .sexo(Sexo.FEMENINO).comprobantePago("pago.pdf").copiaCedula("cedula.pdf")
                    .estado(tipo == TipoUsuario.EXTERNO || tipo == TipoUsuario.ESTUDIANTE_UPSE
                            ? EstadoInscripcion.ACEPTADA : EstadoInscripcion.PENDIENTE).build());
        }
        informes.guardarEgreso(p.getId(), null, new EgresoRequest("Material", 3, new BigDecimal("10")));
        informes.recalcularYGuardar(p.getId()); em.flush(); em.clear();
        var detalle = informes.obtenerDetalle(p.getId());
        assertThat(detalle.getParticipantes()).isEqualTo(2);
        assertThat(detalle.getIngresosTotal()).isEqualByComparingTo("150");
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("230");
        assertThat(detalle.getUtilidad()).isEqualByComparingTo("-80");
    }

    @Test
    void historicoNoPierdeNingunImporteAlRecalcularYSoloCambiaPorEgresoSolicitado() {
        var ids = docentes(1);
        var p = planificar(cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA), ids);
        var historico = informeRepo.findByPlanificacionId(p.getId()).orElseThrow();
        historico.setHistorico(true); historico.setParticipantes(17);
        historico.setIngresosTotal(new BigDecimal("987.65")); historico.setEgresosTotal(new BigDecimal("123.45"));
        historico.setUtilidad(new BigDecimal("864.20")); em.flush();
        informes.recalcularTodo();
        assertThat(informes.obtenerDetalle(p.getId()).getIngresosTotal()).isEqualByComparingTo("987.65");
        var detalle = informes.guardarEgreso(p.getId(), null, new EgresoRequest("Nuevo gasto aprobado", 1, new BigDecimal("20")));
        informes.recalcularTodo(); em.flush(); em.clear();
        detalle = informes.obtenerDetalle(p.getId());
        assertThat(detalle.getParticipantes()).isEqualTo(17);
        assertThat(detalle.getIngresosTotal()).isEqualByComparingTo("987.65");
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("143.45");
        assertThat(detalle.getUtilidad()).isEqualByComparingTo("844.20");
    }

    @Test
    void gastoAnualEsPersistenteYSeRestaUnaSolaVezSinModificarLosCursos() {
        var ids = docentes(1);
        var p = planificar(cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA), ids);
        var inicio = gastos.obtener(2026);
        var resultado = gastos.actualizar(2026, new GastoCecRequest(new BigDecimal("75.25"), inicio.version()));
        em.flush(); em.clear();
        assertThat(gastos.obtener(2026).gastosPersonal()).isEqualByComparingTo("75.25");
        assertThat(resultado.utilidadCec()).isEqualByComparingTo("-275.25");
        assertThat(informes.obtenerDetalle(p.getId()).getEgresosTotal()).isEqualByComparingTo("200");
        assertThat(gastos.obtener(2027).gastosPersonal()).isZero();
    }

    @Test
    void unaEdicionObsoletaNoSobrescribeGastosDeOtraPersona() {
        var primera = gastos.actualizar(2026, new GastoCecRequest(new BigDecimal("10"), -1L));
        gastos.actualizar(2026, new GastoCecRequest(new BigDecimal("20"), primera.version()));
        assertThatThrownBy(() -> gastos.actualizar(2026, new GastoCecRequest(new BigDecimal("30"), primera.version())))
                .hasMessageContaining("otra persona");
    }
}
