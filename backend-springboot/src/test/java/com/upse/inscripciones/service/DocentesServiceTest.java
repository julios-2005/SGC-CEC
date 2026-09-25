package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.EspecialistaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DocentesService resuelve la lista de docentes elegidos al planificar (o al
 * editar la planificación): ids repetidos, nulos o inexistentes se rechazan;
 * "no mandar nada" (null) conserva los docentes anteriores tal cual.
 */
class DocentesServiceTest {

    private EspecialistaRepository repo;
    private DocentesService servicio;

    @BeforeEach
    void setup() {
        repo = mock(EspecialistaRepository.class);
        servicio = new DocentesService(repo);
    }

    private static Especialista especialista(long id) {
        return Especialista.builder().id(id).nombres("Docente" + id).apellidos("Prueba").estado(true).build();
    }

    @Test
    void unaListaDeIdsNulaConservaLosDocentesAnteriores() {
        List<Especialista> anteriores = List.of(especialista(1), especialista(2));

        List<Especialista> resultado = servicio.resolver(null, anteriores);

        assertThat(resultado).containsExactlyElementsOf(anteriores);
    }

    @Test
    void unaListaVaciaDeIdsDejaSinDocentesAunqueHubieraAnteriores() {
        List<Especialista> resultado = servicio.resolver(List.of(), List.of(especialista(1)));

        assertThat(resultado).isEmpty();
    }

    @Test
    void resuelveCadaIdEnElOrdenPedidoConsultandoElRepositorio() {
        when(repo.findById(3L)).thenReturn(Optional.of(especialista(3)));
        when(repo.findById(1L)).thenReturn(Optional.of(especialista(1)));

        List<Especialista> resultado = servicio.resolver(Arrays.asList(3L, 1L), List.of());

        assertThat(resultado).extracting(Especialista::getId).containsExactly(3L, 1L);
    }

    @Test
    void rechazaUnIdQueNoExisteEnElRepositorio() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.resolver(List.of(99L), List.of()))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("No existe el docente seleccionado");
    }

    @Test
    void rechazaIdsRepetidos() {
        assertThatThrownBy(() -> servicio.resolver(Arrays.asList(1L, 1L), List.of()))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("docentes distintos");
    }

    @Test
    void rechazaUnIdNulo() {
        assertThatThrownBy(() -> servicio.resolver(Collections.singletonList(null), List.of()))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("sin campos vacíos");
    }

    @Test
    void rechazaMasDeCienDocentes() {
        List<Long> ids = LongStream.rangeClosed(1, 101).boxed().collect(Collectors.toList());

        assertThatThrownBy(() -> servicio.resolver(ids, List.of()))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("hasta 100");
    }

    @Test
    void exactamenteCienDocentesDistintosSiEsValido() {
        List<Long> ids = LongStream.rangeClosed(1, 100).boxed().collect(Collectors.toList());
        for (Long id : ids) when(repo.findById(id)).thenReturn(Optional.of(especialista(id)));

        assertThat(servicio.resolver(ids, List.of())).hasSize(100);
    }
}
