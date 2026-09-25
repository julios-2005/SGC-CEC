package com.upse.inscripciones.config;

import com.upse.inscripciones.service.InformeEconomicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Recalcula y guarda el Informe Económico de TODAS las planificaciones cada
 * vez que arranca la app. Esto es lo que hace que las planificaciones que ya
 * existían antes de que el informe se empezara a guardar en SQL Server (o
 * cualquier planificación cuyo informe haya quedado desactualizado por algún
 * motivo) queden al día automáticamente, sin tener que tocar nada a mano.
 *
 * Corre después de DatosInicialesRunner (@Order(1)) porque el cálculo
 * necesita que los descuentos ya estén sembrados en la base de datos.
 */
@Component
@RequiredArgsConstructor
@Order(2)
public class InformeEconomicoArranqueRunner implements CommandLineRunner {

    private final InformeEconomicoService informeEconomicoService;

    @Override
    public void run(String... args) {
        informeEconomicoService.recalcularTodo();
    }
}
