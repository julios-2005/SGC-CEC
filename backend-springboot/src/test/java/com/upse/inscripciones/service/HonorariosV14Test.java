package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.*;
import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql=false, properties={"spring.sql.init.mode=never","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
@Import({PlanificacionService.class, InformeEconomicoService.class, DescuentoService.class,
        DocentesService.class, EspecialistaService.class, GastoCecService.class})
class HonorariosV14Test {
    @Autowired PlanificacionService planes;
    @Autowired InformeEconomicoService informes;
    @Autowired EspecialistaService especialistas;
    @Autowired EspecialistaRepository especialistasRepo;
    @Autowired CoordinadorRepository coordinadores;
    @Autowired CursoRepository cursos;
    @Autowired PlanificacionRepository planesRepo;
    @Autowired InformeEconomicoRepository informesRepo;
    @Autowired GastoCecService gastos;
    @Autowired EntityManager em;

    private Especialista docente(String pais) {
        return especialistasRepo.save(Especialista.builder().cedula("DOC"+UUID.randomUUID().toString().substring(0,10).replace("-",""))
                .nombres(pais == null ? "Sin nacionalidad" : "Docente "+pais).apellidos("Prueba").paisNacionalidad(pais).estado(true).build());
    }
    private PlanificacionResponse guardar(List<Especialista> docentes, List<BigDecimal> honorarios, String total) {
        var c = cursos.save(Curso.builder().nombre("Curso "+UUID.randomUUID()).codigo("T"+UUID.randomUUID().toString().substring(0,8))
                .horas(40).costo(new BigDecimal("100")).cuposTotales(30).cuposRestantes(30)
                .estado(EstadoCurso.EN_ESPERA).modalidad(Modalidad.VIRTUAL).ambito(AmbitoCurso.NACIONAL).build());
        var coordinador = coordinadores.save(Coordinador.builder().cedula("CO"+UUID.randomUUID().toString().substring(0,10))
                .nombres("Coordinadora").apellidos("Prueba").estado(true).build());
        return planes.guardarConDocentes(null,c.getIdCurso(),coordinador.getId(),docentes.get(0).getId(),
                LocalDate.of(2026,11,1),"18:00",LocalDate.of(2026,11,30),Modalidad.VIRTUAL,
                total == null ? null : new BigDecimal(total),docentes.stream().map(Especialista::getId).toList(),honorarios);
    }
    @Test void nacionalExtranjeroYNoEspecificadoSeCalculanIndividualmenteYConRedondeo() {
        var p = guardar(List.of(docente("EC"),docente("CO"),docente(null)),
                List.of(new BigDecimal("1000.03"),new BigDecimal("123.45"),new BigDecimal("50")),"1173.48");
        em.flush(); em.clear();
        var leido = planes.obtenerPorId(p.getId());
        assertThat(leido.getHonorariosDocentes()).hasSize(3);
        assertThat(leido.getHonorariosDocentes().get(0).costoTransferencia()).isZero();
        assertThat(leido.getHonorariosDocentes().get(1).costoTransferencia()).isEqualByComparingTo("30.86");
        // pagoNeto = honorario + transferencia: costo TOTAL de este especialista para el CEC.
        assertThat(leido.getHonorariosDocentes().get(1).pagoNeto()).isEqualByComparingTo("154.31");
        assertThat(leido.getHonorariosDocentes().get(2).costoTransferencia()).isZero();
        var detalle=informes.obtenerDetalle(p.getId());
        assertThat(detalle.getEgresos()).hasSize(4);
        // 1173.48 (suma de los tres honorarios brutos) + 30.86 (25% adicional
        // del especialista extranjero) = 1204.34: la transferencia se SUMA,
        // no se descuenta de ningún honorario.
        assertThat(detalle.getEgresos().stream().map(EgresoLineaResponse::getTotal).reduce(BigDecimal.ZERO,BigDecimal::add))
                .isEqualByComparingTo("1204.34");
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("1204.34");
    }
    @Test void milBrutosGeneranMilDeHonorarioMas250DeTransferenciaSonMilDoscientosCincuentaDeGasto() {
        var p=guardar(List.of(docente("ES")),List.of(new BigDecimal("1000")),"1000");
        var detalle=informes.obtenerDetalle(p.getId());
        // El especialista extranjero recibe su honorario COMPLETO (1000); el
        // 25% de transferencia ($250) es un costo adicional que asume el
        // CEC, no una deducción del honorario. Egreso total: 1250.
        assertThat(detalle.getEgresos()).extracting(EgresoLineaResponse::getTotal)
                .usingComparatorForType(BigDecimal::compareTo,BigDecimal.class)
                .containsExactly(new BigDecimal("1000"),new BigDecimal("250"));
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("1250");
        informes.guardarEgreso(p.getId(),null,new EgresoRequest("Materiales",2,new BigDecimal("7.50")));
        informes.recalcularYGuardar(p.getId()); informes.recalcularYGuardar(p.getId()); em.flush(); em.clear();
        assertThat(informes.obtenerDetalle(p.getId()).getEgresosTotal()).isEqualByComparingTo("1265");
        assertThat(informes.obtenerDetalle(p.getId()).getEgresos()).hasSize(3);
    }
    @Test void cambiarNacionalidadActualizaElDesgloseSinDuplicarElEgreso() {
        var d=docente("CO"); var p=guardar(List.of(d),List.of(new BigDecimal("1000")),"1000");
        especialistas.actualizar(d.getId(),d.getCedula(),d.getNombres(),d.getApellidos(),null,null,null,null,"EC");
        em.flush(); em.clear();
        assertThat(informes.obtenerDetalle(p.getId()).getEgresos()).hasSize(1);
        assertThat(informes.obtenerDetalle(p.getId()).getEgresosTotal()).isEqualByComparingTo("1000");
    }
    @Test void noAdivinaElRepartoDeUnTotalConVariosDocentesExtranjeros() {
        assertThatThrownBy(() -> guardar(List.of(docente("EC"),docente("PE")),null,"1000"))
                .hasMessageContaining("honorario individual");
        assertThat(planesRepo.count()).isZero();
    }
    @Test void rechazaHonorariosQueNoCoincidenConElTotal() {
        assertThatThrownBy(() -> guardar(List.of(docente("CO")),List.of(new BigDecimal("900")),"1000"))
                .hasMessageContaining("coincidir");
        assertThat(planesRepo.count()).isZero();
    }
    @Test void referenciaAgregadaConservaSuImporteSinInventarUnaTarifaNiUnaPlanificacion() {
        var ref=InformeEconomico.builder().nombreReferencia("CURSO BECADOS 2026-1 (2 CURSOS)").anioReferencia(2026)
                .referenciaExcel("REF-PRUEBA").historico(true).participantes(95).ingresosTotal(new BigDecimal("4870"))
                .egresosTotal(new BigDecimal("3270")).utilidad(new BigDecimal("1600")).build();
        ref.getLineasIngreso().add(InformeEconomicoLineaIngreso.builder().informeEconomico(ref)
                .etiqueta("TOTAL SEGÚN RESUMEN CEC").cantidad(95).valorUnitario(null).total(new BigDecimal("4870")).build());
        informesRepo.saveAndFlush(ref); em.clear();
        var detalle=informes.obtenerDetalle(-ref.getId());
        assertThat(detalle.getIdPlanificacion()).isNull(); assertThat(detalle.getFechaInicio()).isNull();
        assertThat(detalle.getIngresos().get(0).getValorUnitario()).isNull();
        assertThat(gastos.obtener(2026).ingresos()).isEqualByComparingTo("4870");
        assertThat(gastos.obtener(2026).participantes()).isEqualTo(95);
        assertThat(informes.listarResumen(null,Modalidad.VIRTUAL,null,null)).isEmpty();
        assertThat(informes.listarResumen(null,null,LocalDate.of(2026,1,1),null)).isEmpty();
    }
}
