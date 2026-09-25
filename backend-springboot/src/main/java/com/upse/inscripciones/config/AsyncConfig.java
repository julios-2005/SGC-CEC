package com.upse.inscripciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Habilita @Async y define el pool de hilos que lo respalda.
 * <p>
 * Se usa hoy solo para el envío de correos (comprobante de matrícula al
 * aceptar una inscripción), para que esa llamada a Gmail SMTP no bloquee
 * la respuesta HTTP del administrador que está aceptando al estudiante.
 * <p>
 * Importante: sin este bean, @Async usaría el ejecutor por defecto de
 * Spring, que crea un hilo nuevo sin límite por cada llamada — bien para
 * uso ocasional, riesgoso si alguna vez se aceptan muchas inscripciones
 * en poco tiempo (ej. cierre de una convocatoria). Por eso se define un
 * pool acotado en vez de dejar el comportamiento por defecto.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Nombre "taskExecutor": Spring lo detecta automáticamente como el
     * ejecutor por defecto para cualquier @Async que no especifique uno
     * explícito, sin necesidad de nombrarlo en cada anotación.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");

        // Apagado ordenado: si el servidor se detiene mientras hay correos
        // en camino, espera hasta 20s a que terminen en vez de cortarlos.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);

        executor.initialize();
        return executor;
    }
}
