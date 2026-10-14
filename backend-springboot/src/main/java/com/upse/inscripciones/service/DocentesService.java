package com.upse.inscripciones.service;

import com.upse.inscripciones.entity.Especialista;
import com.upse.inscripciones.exception.ValidacionException;
import com.upse.inscripciones.repository.EspecialistaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocentesService {
    private final EspecialistaRepository repository;

    public List<Especialista> resolver(List<Long> ids, List<Especialista> anteriores) {
        if (ids == null) return new ArrayList<>(anteriores);
        if (ids.size() > 100 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size())
            throw new ValidacionException("Selecciona docentes distintos (hasta 100), sin campos vacíos.");
        List<Especialista> resultado = new ArrayList<>();
        for (Long id : ids) {
            Especialista e = repository.findById(id)
                    .orElseThrow(() -> new ValidacionException("No existe el docente seleccionado: " + id));
            // El estado del especialista ya no es un permiso de cuenta: ver
            // la nota en PlanificacionService#resolverYValidarEntidadesRelacionadas.
            resultado.add(e);
        }
        return resultado;
    }
}
