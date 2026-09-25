package com.upse.inscripciones.config;

import com.upse.inscripciones.service.EspecialistaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "Activo"/"Disponible" en Especialista ya no es un interruptor manual: se
 * recalcula solo, desde PlanificacionService cada vez que se guarda o
 * elimina una planificación (ver EspecialistaService#recalcularEstado). Pero
 * una planificación también puede EMPEZAR o TERMINAR sin que nadie haga
 * ninguna acción ese día (el fechaInicio/fechaFin simplemente llega), así
 * que hace falta este recálculo diario para no depender de que alguien
 * entre al sistema justo ese día.
 *
 * run() además lo hace una vez al arrancar la app, para corregir cualquier
 * estado que haya quedado desactualizado mientras el servidor estuvo
 * apagado.
 */
@Component
@RequiredArgsConstructor
public class EspecialistaEstadoScheduler implements CommandLineRunner {

    private final EspecialistaService especialistaService;

    @Override
    public void run(String... args) {
        especialistaService.recalcularEstadoDeTodos();
    }

    // Todos los días a las 00:05 (hora del servidor).
    @Scheduled(cron = "0 5 0 * * *")
    public void recalcularDiario() {
        especialistaService.recalcularEstadoDeTodos();
    }
}
