package com.upse.inscripciones.service;

import com.upse.inscripciones.dto.CursoResponse;
import com.upse.inscripciones.entity.Curso;
import com.upse.inscripciones.entity.EstadoCurso;
import com.upse.inscripciones.entity.Modalidad;
import com.upse.inscripciones.exception.RecursoNoEncontradoException;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.CursoRepository;
import com.upse.inscripciones.repository.PlanificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de Curso: unicidad de nombre y código, validación de los campos
 * numéricos, la relación cuposRestantes/cuposTotales al actualizar (no se
 * pueden perder cupos ya ocupados), y que editar el costo de un curso
 * dispare el recálculo del Informe Económico de sus planificaciones.
 */
class CursoServiceTest {

    private CursoRepository repo;
    private FileStorageService archivos;
    private InformeEconomicoService informes;
    private PlanificacionRepository planificaciones;
    private CursoService servicio;

    @BeforeEach
    void setup() {
        repo = mock(CursoRepository.class);
        archivos = mock(FileStorageService.class);
        informes = mock(InformeEconomicoService.class);
        planificaciones = mock(PlanificacionRepository.class);
        servicio = new CursoService(repo, archivos, informes, planificaciones);
        when(repo.save(any(Curso.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Curso existente(int cuposTotales, int cuposRestantes) {
        return Curso.builder().idCurso(1L).nombre("Diplomado en X").codigo("COD-1")
                .horas(40).costo(new BigDecimal("100.00")).cuposTotales(cuposTotales)
                .cuposRestantes(cuposRestantes).modalidad(Modalidad.VIRTUAL).estado(EstadoCurso.EN_ESPERA).build();
    }

    // ── registrar ────────────────────────────────────────────────────────────

    @Test
    void registrarConCuposRestantesNoIndicadosLosIgualaALosTotales() {
        CursoResponse r = servicio.registrar("Diplomado en X", "COD-1", 40, new BigDecimal("100.00"),
                30, null, Modalidad.VIRTUAL, null, null);

        assertThat(r.getCuposRestantes()).isEqualTo(30);
        assertThat(r.getEstado()).isEqualTo(EstadoCurso.EN_ESPERA);
    }

    @Test
    void registrarRespetaCuposRestantesYEstadoIndicados() {
        CursoResponse r = servicio.registrar("Diplomado en X", "COD-1", 40, new BigDecimal("100.00"),
                30, 20, Modalidad.VIRTUAL, EstadoCurso.EN_PROCESO, null);

        assertThat(r.getCuposRestantes()).isEqualTo(20);
        assertThat(r.getEstado()).isEqualTo(EstadoCurso.EN_PROCESO);
    }

    @Test
    void registrarRechazaCamposInvalidosSinGuardar() {
        assertThatThrownBy(() -> servicio.registrar(" ", "C", 10, BigDecimal.TEN, 5, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("nombre del curso");
        assertThatThrownBy(() -> servicio.registrar("N", " ", 10, BigDecimal.TEN, 5, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("código del curso");
        assertThatThrownBy(() -> servicio.registrar("N", "C", 0, BigDecimal.TEN, 5, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Las horas");
        assertThatThrownBy(() -> servicio.registrar("N", "C", 10, new BigDecimal("-1"), 5, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("costo");
        assertThatThrownBy(() -> servicio.registrar("N", "C", 10, BigDecimal.TEN, 0, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("cupos totales");
        assertThatThrownBy(() -> servicio.registrar("N", "C", 10, BigDecimal.TEN, 5, 6, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("cupos restantes");
        assertThatThrownBy(() -> servicio.registrar("N", "C", 10, BigDecimal.TEN, 5, -1, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("cupos restantes");
        assertThatThrownBy(() -> servicio.registrar("N", "C", 10, BigDecimal.TEN, 5, null, null, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("modalidad");

        verify(repo, never()).save(any());
    }

    @Test
    void registrarRechazaNombreOCodigoYaExistentesSinDistinguirMayusculas() {
        when(repo.existsByNombreIgnoreCase("diplomado en x")).thenReturn(true);
        assertThatThrownBy(() -> servicio.registrar("diplomado en x", "COD-2", 40, BigDecimal.TEN, 10, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un curso registrado con el nombre");

        when(repo.existsByCodigoIgnoreCase("cod-1")).thenReturn(true);
        assertThatThrownBy(() -> servicio.registrar("Otro", "cod-1", 40, BigDecimal.TEN, 10, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("Ya existe un curso registrado con el código");

        verify(repo, never()).save(any());
    }

    // ── actualizar ───────────────────────────────────────────────────────────

    @Test
    void actualizarConservandoSuPropioNombreYCodigoNoLosTomaPorDuplicados() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));

        servicio.actualizar(1L, "Diplomado en X", "COD-1", 40, BigDecimal.TEN, 30, 20, Modalidad.VIRTUAL, null, null);

        verify(repo, never()).existsByNombreIgnoreCase(anyString());
        verify(repo, never()).existsByCodigoIgnoreCase(anyString());
    }

    @Test
    void noSePuedenReducirLosCuposTotalesPorDebajoDeLosYaOcupados() {
        // 30 totales, 20 restantes -> 10 ocupados. Bajar a 5 totales es inválido.
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));

        assertThatThrownBy(() -> servicio.actualizar(1L, "Diplomado en X", "COD-1", 40, BigDecimal.TEN, 5, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("no pueden ser menores que los cupos ya ocupados");
    }

    @Test
    void alSubirLosCuposTotalesSinIndicarRestantesSeMantienenLosOcupados() {
        // 30 totales, 20 restantes -> 10 ocupados. Subir a 50 sin indicar
        // restantes debe dejar 40 restantes (50 - 10 ocupados).
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));

        CursoResponse r = servicio.actualizar(1L, "Diplomado en X", "COD-1", 40, BigDecimal.TEN, 50, null, Modalidad.VIRTUAL, null, null);

        assertThat(r.getCuposRestantes()).isEqualTo(40);
    }

    @Test
    void actualizarElCostoDisparaElRecalculoDelInformeEconomico() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));

        servicio.actualizar(1L, "Diplomado en X", "COD-1", 40, new BigDecimal("150.00"), 30, 20, Modalidad.VIRTUAL, null, null);

        verify(informes).recalcularPorCurso(1L);
    }

    @Test
    void unEstadoNuloAlActualizarConservaElEstadoAnterior() {
        Curso c = existente(30, 20);
        c.setEstado(EstadoCurso.EN_PROCESO);
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        CursoResponse r = servicio.actualizar(1L, "Diplomado en X", "COD-1", 40, BigDecimal.TEN, 30, 20, Modalidad.VIRTUAL, null, null);

        assertThat(r.getEstado()).isEqualTo(EstadoCurso.EN_PROCESO);
    }

    @Test
    void actualizarSinFotoNuevaConservaLaFotoAnterior() {
        Curso c = existente(30, 20);
        c.setFoto("fotos-cursos/vieja.jpg");
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        CursoResponse r = servicio.actualizar(1L, "Diplomado en X", "COD-1", 40, BigDecimal.TEN, 30, 20, Modalidad.VIRTUAL, null, null);

        assertThat(r.getFoto()).isEqualTo("fotos-cursos/vieja.jpg");
    }

    @Test
    void actualizarUnCursoInexistenteSeReportaComoNoEncontrado() {
        when(repo.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(9L, "N", "C", 10, BigDecimal.TEN, 5, null, Modalidad.VIRTUAL, null, null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── cambiar estado / eliminar ────────────────────────────────────────────

    @Test
    void cambiarEstadoExigeUnEstadoNoNulo() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));

        assertThatThrownBy(() -> servicio.cambiarEstado(1L, null))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("estado es obligatorio");
    }

    @Test
    void cambiarEstadoActualizaElCurso() {
        Curso c = existente(30, 20);
        when(repo.findById(1L)).thenReturn(Optional.of(c));

        servicio.cambiarEstado(1L, EstadoCurso.FINALIZADO);

        assertThat(c.getEstado()).isEqualTo(EstadoCurso.FINALIZADO);
    }

    @Test
    void noSePuedeEliminarUnCursoConUnaPlanificacionAsignada() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));
        when(planificaciones.existsByCursoIdCurso(1L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.eliminar(1L))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("planificación activa con ese curso");

        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void sinPlanificacionesSiSePuedeEliminar() {
        when(repo.findById(1L)).thenReturn(Optional.of(existente(30, 20)));
        when(planificaciones.existsByCursoIdCurso(1L)).thenReturn(false);

        servicio.eliminar(1L);

        verify(repo).deleteById(1L);
    }
}
