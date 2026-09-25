package com.upse.inscripciones;
 
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
 
// La API usa sus usuarios de PostgreSQL y JWT; no una cuenta Spring "user" generada.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
// Habilita @Scheduled: lo usa EspecialistaEstadoScheduler para el recálculo
// diario de Activo/Disponible de los especialistas.
@EnableScheduling
public class InscripcionesApplication {
 
    public static void main(String[] args) {
        SpringApplication.run(InscripcionesApplication.class, args);
    }
}
