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
 * Los docentes se eligen al planificar (no se copian del curso) y una planificación con docentes inválidos no queda creada a medias.
 */
@DataJpaTest(showSql = false, properties = {
    "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({CursoService.class, PlanificacionService.class, EspecialistaService.class, InformeEconomicoService.class,
        DescuentoService.class, DocentesService.class, GastoCecService.class})
class PlanificacionDocentesTest {
    @Autowired CursoService cursos;
    @Autowired PlanificacionService planificaciones;
    @Autowired InformeEconomicoService informes;
    @Autowired PlanificacionRepository planRepo;
    @Autowired EspecialistaRepository especialistaRepo;
    @Autowired CoordinadorRepository coordinadorRepo;
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
    void losDocentesSeEligenAlPlanificarYNoSeCopianDelCurso() {
        var ids = docentes(5);
        var curso = cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA);
        var p = planificar(curso, ids);
        em.flush(); em.clear();
        assertThat(cursos.obtenerPorId(curso.getIdCurso()).getAmbito()).isEqualTo(AmbitoCurso.INTERNACIONAL);
        assertThat(planificaciones.obtenerPorId(p.getId()).getDocentes()).hasSize(5);
        var detalle = informes.obtenerDetalle(p.getId());
        assertThat(detalle.getEspecialista()).contains("Docente 0", "Docente 4");
        assertThat(detalle.getParticipantes()).isZero();
        assertThat(detalle.getEgresosTotal()).isEqualByComparingTo("200");
        assertThat(planificaciones.listarPorEspecialista(ids.get(4))).hasSize(1);
        assertThat(planificaciones.listarTodas("Docente 4", null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent()).hasSize(1);
        Collections.reverse(ids);
        planificaciones.guardarConDocentes(p.getId(), curso.getIdCurso(), p.getCoordinador().getId(),
                ids.get(0), p.getFechaInicio(), p.getHorario(), p.getFechaFin(), p.getModalidad(),
                p.getCostoEspecialista(), ids);
        em.flush(); em.clear();
        assertThat(planificaciones.obtenerPorId(p.getId()).getDocentes()).extracting(DocenteResponse::id).containsExactlyElementsOf(ids);
    }

    @Test
    void docentesInvalidosNoDejanUnaPlanificacionCreadaAmedias() {
        var ids = docentes(1);
        var curso = cursos.guardarFormulario(null, solicitud(), Modalidad.VIRTUAL, EstadoCurso.EN_ESPERA);
        var c = coordinadorRepo.save(Coordinador.builder().cedula("0912345678").nombres("Ana")
                .apellidos("Prueba").estado(true).build());
        assertThatThrownBy(() -> planificaciones.guardarConDocentes(null, curso.getIdCurso(), c.getId(), ids.get(0),
                LocalDate.of(2026, 11, 1), "18:00", LocalDate.of(2026, 11, 30), Modalidad.VIRTUAL,
                new BigDecimal("200.00"), List.of(ids.get(0), ids.get(0))))
                .hasMessageContaining("distintos");
        assertThat(planRepo.count()).isZero();
    }
}
