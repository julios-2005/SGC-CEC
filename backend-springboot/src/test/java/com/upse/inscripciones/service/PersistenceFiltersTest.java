package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.*;
import com.upse.inscripciones.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas aisladas en H2: nunca conectan ni alteran la base del usuario. */
@DataJpaTest(showSql = false, properties = {
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({CursoService.class, PlanificacionService.class, EspecialistaService.class, InformeEconomicoService.class, DescuentoService.class, InscripcionService.class, UsuarioService.class, DocentesService.class})
class PersistenceFiltersTest {
    @Autowired CursoRepository courses;
    @Autowired CoordinadorRepository coordinators;
    @Autowired EspecialistaRepository specialists;
    @Autowired PlanificacionRepository plans;
    @Autowired InformeEconomicoRepository reports;
    @Autowired InscripcionRepository registrations;
    @Autowired CursoService courseService;
    @Autowired PlanificacionService planningService;
    @Autowired InformeEconomicoService reportService;
    @Autowired InscripcionService registrationService;
    @Autowired UsuarioService userService;
    @Autowired UsuarioRepository users;
    @MockBean AuthService auth;
    @MockBean FileStorageService files;
    @MockBean ComprobanteMatriculaPdfService pdf;
    @MockBean EmailService mail;
    private Planificacion virtual;

    @BeforeEach
    void seed() {
        Coordinador coordinator = coordinators.save(Coordinador.builder().cedula("1234567890").nombres("Coord").apellidos("Prueba").estado(true).build());
        Especialista specialist = specialists.save(Especialista.builder().cedula("9876543210").nombres("Especialista").apellidos("Prueba").estado(true).build());
        for (Modalidad modality : Modalidad.values()) {
            EstadoCurso state = modality == Modalidad.VIRTUAL ? EstadoCurso.EN_ESPERA : modality == Modalidad.PRESENCIAL ? EstadoCurso.FINALIZADO : EstadoCurso.EN_PROCESO;
            Curso course = courses.save(Curso.builder().nombre("Curso " + modality).codigo(modality.name()).horas(20).costo(new BigDecimal("50.00"))
                    .cuposTotales(30).cuposRestantes(30).modalidad(modality).estado(state).build());
            Planificacion plan = plans.save(Planificacion.builder().curso(course).coordinador(coordinator).especialista(specialist)
                    .fechaInicio(LocalDate.of(2026, 9, 1)).fechaFin(LocalDate.of(2026, 9, 30)).horario("18:00 - 20:00")
                    .modalidad(modality).costoEspecialista(new BigDecimal("20.00")).build());
            reports.save(InformeEconomico.builder().planificacion(plan).participantes(4).ingresosTotal(new BigDecimal("200.00"))
                    .egresosTotal(new BigDecimal("20.00")).utilidad(new BigDecimal("180.00")).historico(true).build());
            if (modality == Modalidad.VIRTUAL) virtual = plan;
        }
        reports.flush();
    }

    @Test
    void listadoInicialSinFiltrosNoPierdeNingunRegistroYPaginaCorrectamente() {
        var result = courseService.listarTodos(null, null, null, PageRequest.of(0, 1));
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(1);
        assertThat(planningService.listarTodas(null, null, null, null, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(3);
        assertThat(reportService.listarResumen()).hasSize(3);
    }

    @Test
    void estadosEnEsperaYFinalizadoNoSeIntercambian() {
        var waiting = courseService.listarTodos(null, EstadoCurso.EN_ESPERA, null, PageRequest.of(0, 20));
        var finished = courseService.listarTodos(null, EstadoCurso.FINALIZADO, null, PageRequest.of(0, 20));
        assertThat(waiting.getContent()).singleElement().satisfies(item -> assertThat(item.getNombre()).isEqualTo("Curso VIRTUAL"));
        assertThat(finished.getContent()).singleElement().satisfies(item -> assertThat(item.getNombre()).isEqualTo("Curso PRESENCIAL"));
    }

    @Test
    void modalidadCoincideEnCursosPlanificacionEInforme() {
        for (Modalidad modality : Modalidad.values()) {
            assertThat(courseService.listarTodos(null, null, modality, PageRequest.of(0, 20)).getContent())
                    .singleElement().satisfies(item -> assertThat(item.getModalidad()).isEqualTo(modality));
            assertThat(planningService.listarTodas(null, modality, null, null, PageRequest.of(0, 20)).getContent())
                    .singleElement().satisfies(item -> assertThat(item.getModalidad()).isEqualTo(modality));
            assertThat(reportService.listarResumen(null, modality, null, null))
                    .singleElement().satisfies(item -> assertThat(item.getModalidad()).isEqualTo(modality));
        }
    }

    @Test
    void distingueYPermiteBuscarCadaProgramacionDelMismoCurso() {
        Planificacion segunda = plans.save(Planificacion.builder()
                .curso(virtual.getCurso()).coordinador(virtual.getCoordinador()).especialista(virtual.getEspecialista())
                .fechaInicio(LocalDate.of(2026, 10, 1)).fechaFin(LocalDate.of(2026, 10, 31))
                .horario("18:00 - 20:00").modalidad(Modalidad.VIRTUAL)
                .costoEspecialista(new BigDecimal("20.00")).build());
        reports.saveAndFlush(InformeEconomico.builder().planificacion(segunda).participantes(2)
                .ingresosTotal(new BigDecimal("100.00")).egresosTotal(new BigDecimal("20.00"))
                .utilidad(new BigDecimal("80.00")).historico(true).build());

        assertThat(reportService.listarResumen("programación", Modalidad.VIRTUAL, null, null))
                .extracting(com.upse.inscripciones.dto.InformeEconomicoResumenResponse::getNombreCurso)
                .containsExactly(
                        "Curso VIRTUAL - Primera programación",
                        "Curso VIRTUAL - Segunda programación");
        assertThat(reportService.listarResumen("Segunda programación", null, null, null))
                .singleElement()
                .satisfies(item -> assertThat(item.getIdPlanificacion()).isEqualTo(segunda.getId()));
    }

    @Test
    void muestraLosEgresosYLaUtilidadQueFaltabanEnElResumen() {
        Curso curso = courses.save(Curso.builder().nombre("Pendiente oficial").codigo("MIG-SUF-A2")
                .horas(20).costo(new BigDecimal("100.00")).cuposTotales(30).cuposRestantes(30)
                .modalidad(Modalidad.VIRTUAL).estado(EstadoCurso.EN_ESPERA).build());
        Planificacion plan = plans.save(Planificacion.builder().curso(curso)
                .coordinador(virtual.getCoordinador()).especialista(virtual.getEspecialista())
                .fechaInicio(LocalDate.of(2026, 8, 24)).fechaFin(LocalDate.of(2026, 9, 30))
                .horario("18:00 - 20:00").modalidad(Modalidad.VIRTUAL)
                .costoEspecialista(new BigDecimal("900.00")).build());
        reports.saveAndFlush(InformeEconomico.builder().planificacion(plan).participantes(9)
                .ingresosTotal(new BigDecimal("870.00")).egresosTotal(new BigDecimal("900.00"))
                .utilidad(new BigDecimal("-30.00")).historico(true).build());

        assertThat(reportService.listarResumen("Pendiente oficial", null, null, null))
                .singleElement().satisfies(item -> {
                    assertThat(item.getIngresos()).isEqualByComparingTo("870.00");
                    assertThat(item.getEgresos()).isEqualByComparingTo("900.00");
                    assertThat(item.getUtilidad()).isEqualByComparingTo("-30.00");
                });
    }

    @Test
    void textoYFechasSeCombinanConModalidadYExportacion() {
        var from = LocalDate.of(2026, 9, 1);
        var to = LocalDate.of(2026, 9, 30);
        assertThat(planningService.listarTodas("virtual", Modalidad.VIRTUAL, from, to, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(planningService.listarParaExportar("virtual", Modalidad.VIRTUAL, from, to)).hasSize(1);
        assertThat(reportService.listarResumen("presencial", Modalidad.VIRTUAL, from, to)).isEmpty();
        assertThat(reportService.listarParaExportar(null, Modalidad.PRESENCIAL, from, to)).hasSize(1);
        assertThat(planningService.listarTodas(null, null, to.plusDays(1), null, PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void recalcularNoPoneEnCeroLosInformesHistoricosDelRespaldo() {
        reportService.recalcularYGuardar(virtual.getId());
        InformeEconomico saved = reports.findByPlanificacionId(virtual.getId()).orElseThrow();
        assertThat(saved.getParticipantes()).isEqualTo(4);
        assertThat(saved.getIngresosTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void rechazarDosVecesNoLiberaDosCupos() {
        Long courseId = virtual.getCurso().getIdCurso();
        Inscripcion registration = registrations.saveAndFlush(Inscripcion.builder().planificacion(virtual).tipoUsuario(TipoUsuario.EXTERNO)
                .nombreCompleto("Estudiante de Prueba").cedula("1234567890").telefono("+593991234567")
                .correoElectronico("prueba@example.test").direccion("Dirección de prueba").sexo(Sexo.MASCULINO)
                .comprobantePago("prueba/pago.pdf").copiaCedula("prueba/cedula.pdf").estado(EstadoInscripcion.PENDIENTE).build());
        assertThat(courses.decrementarCupoSiHayDisponible(courseId)).isEqualTo(1);
        registrationService.cambiarEstado(registration.getId(), EstadoInscripcion.RECHAZADA);
        registrationService.cambiarEstado(registration.getId(), EstadoInscripcion.RECHAZADA);
        assertThat(courses.findById(courseId).orElseThrow().getCuposRestantes()).isEqualTo(30);
    }

    @Test
    void ordenAlfabeticoDeUsuariosIncluyeElNombreDelCoordinador() {
        users.save(Usuario.builder().nombreUsuario("zeta").nombreCompleto("Zeta Prueba").contraseña("hash-de-prueba").rol(Rol.ADMIN_GENERAL).build());
        users.save(Usuario.builder().nombreUsuario("alfa.sin.nombre").contraseña("hash-de-prueba").rol(Rol.ADMIN_GENERAL).build());
        users.saveAndFlush(Usuario.builder().nombreUsuario("cuenta.coordinador").contraseña("hash-de-prueba").rol(Rol.COORDINADOR).coordinador(virtual.getCoordinador()).build());
        assertThat(userService.obtenerTodos(null, null, null, PageRequest.of(0, 20, Sort.by("nombreCompleto"))).getContent())
                .extracting(com.upse.inscripciones.dto.UsuarioResponse::getNombreUsuario).containsExactly("alfa.sin.nombre", "cuenta.coordinador", "zeta");
        assertThat(userService.obtenerTodos(null, Rol.COORDINADOR, true, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void liberarUnCupoNoExcedeElTotalDespuesDeUnAjusteManual() {
        Long id = virtual.getCurso().getIdCurso();
        courses.incrementarCupo(id);
        assertThat(courses.findById(id).orElseThrow().getCuposRestantes()).isEqualTo(30);
    }

    @Test
    void catalogoPublicoExcluyePlanificacionesVencidasYCursosFinalizados() {
        LocalDate hoy = LocalDate.now();
        Curso activo = courses.save(Curso.builder().nombre("Curso público activo").codigo("PUBLICO-ACTIVO")
                .horas(20).costo(new BigDecimal("50.00")).cuposTotales(10).cuposRestantes(10)
                .modalidad(Modalidad.VIRTUAL).estado(EstadoCurso.EN_PROCESO).build());
        Curso finalizado = courses.save(Curso.builder().nombre("Curso público finalizado").codigo("PUBLICO-FIN")
                .horas(20).costo(new BigDecimal("50.00")).cuposTotales(10).cuposRestantes(10)
                .modalidad(Modalidad.VIRTUAL).estado(EstadoCurso.FINALIZADO).build());
        Planificacion vigente = plans.save(Planificacion.builder().curso(activo)
                .coordinador(virtual.getCoordinador()).especialista(virtual.getEspecialista())
                .fechaInicio(hoy.minusDays(1)).fechaFin(hoy.plusDays(5)).horario("18:00")
                .modalidad(Modalidad.VIRTUAL).build());
        Planificacion vencida = plans.save(Planificacion.builder().curso(activo)
                .coordinador(virtual.getCoordinador()).especialista(virtual.getEspecialista())
                .fechaInicio(hoy.minusDays(10)).fechaFin(hoy.minusDays(1)).horario("18:00")
                .modalidad(Modalidad.VIRTUAL).build());
        Planificacion planFinalizado = plans.saveAndFlush(Planificacion.builder().curso(finalizado)
                .coordinador(virtual.getCoordinador()).especialista(virtual.getEspecialista())
                .fechaInicio(hoy.minusDays(1)).fechaFin(hoy.plusDays(5)).horario("18:00")
                .modalidad(Modalidad.VIRTUAL).build());

        assertThat(planningService.listarVigentes())
                .extracting(com.upse.inscripciones.dto.PlanificacionResponse::getId)
                .contains(vigente.getId())
                .doesNotContain(vencida.getId(), planFinalizado.getId());
        assertThat(planningService.listarVigentes())
                .extracting(item -> item.getCurso().getNombre())
                .contains("Curso público activo")
                .doesNotContain("Curso público finalizado");
    }

    @Test
    void apiDeInscripcionRechazaEnlaceVencidoOAUnCursoFinalizado() {
        virtual.setFechaFin(LocalDate.now().minusDays(1));
        plans.saveAndFlush(virtual);

        assertThatThrownBy(() -> registrationService.registrarInscripcion(
                TipoUsuario.EXTERNO, "Persona vencida", virtual.getId(), "0911111111",
                "+593991111111", "vencida@example.test", "Dirección", Sexo.FEMENINO,
                null, null, null))
                .isInstanceOf(com.upse.inscripciones.exception.ValidacionException.class)
                .hasMessageContaining("finalizó");

        virtual.setFechaFin(LocalDate.now().plusDays(1));
        virtual.getCurso().setEstado(EstadoCurso.FINALIZADO);
        courses.saveAndFlush(virtual.getCurso());
        plans.saveAndFlush(virtual);

        assertThatThrownBy(() -> registrationService.registrarInscripcion(
                TipoUsuario.EXTERNO, "Persona finalizada", virtual.getId(), "0922222222",
                "+593992222222", "finalizada@example.test", "Dirección", Sexo.FEMENINO,
                null, null, null))
                .isInstanceOf(com.upse.inscripciones.exception.ValidacionException.class)
                .hasMessageContaining("curso seleccionado está finalizado");
    }

    @Test
    void noPermiteCambiarElCursoDeUnaPlanificacionConInscritos() {
        registrations.saveAndFlush(Inscripcion.builder().planificacion(virtual).tipoUsuario(TipoUsuario.EXTERNO)
                .nombreCompleto("Estudiante relacionado").cedula("0999999999").telefono("+593999999999")
                .correoElectronico("relacionado@example.test").direccion("Dirección")
                .sexo(Sexo.MASCULINO).comprobantePago("pago.pdf").copiaCedula("cedula.pdf")
                .estado(EstadoInscripcion.PENDIENTE).build());
        Curso otroCurso = courses.findAll().stream()
                .filter(item -> !item.getIdCurso().equals(virtual.getCurso().getIdCurso()))
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> planningService.actualizarPlanificacion(
                virtual.getId(), otroCurso.getIdCurso(), virtual.getCoordinador().getId(),
                virtual.getEspecialista().getId(), virtual.getFechaInicio(), virtual.getHorario(),
                virtual.getFechaFin(), virtual.getModalidad(), virtual.getCostoEspecialista()))
                .isInstanceOf(com.upse.inscripciones.exception.ValidacionException.class)
                .hasMessageContaining("ya tiene inscripciones");

        assertThat(plans.findById(virtual.getId()).orElseThrow().getCurso().getIdCurso())
                .isEqualTo(virtual.getCurso().getIdCurso());
    }
}
