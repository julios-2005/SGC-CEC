package com.upse.inscripciones.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.upse.inscripciones.config.TicsApiProperties;
import com.upse.inscripciones.exception.ConsultaInstitucionalException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ConsultaInstitucionalRateLimiter {
    private final Cache<String, AtomicInteger> intentos = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(1)).build();
    private final TicsApiProperties properties;

    public ConsultaInstitucionalRateLimiter(TicsApiProperties properties) { this.properties = properties; }

    public void comprobar(String ip) {
        if (intentos.get(ip == null ? "desconocida" : ip, key -> new AtomicInteger()).incrementAndGet()
                > Math.max(1, properties.getConsultasPorMinuto())) {
            throw new ConsultaInstitucionalException(HttpStatus.TOO_MANY_REQUESTS, "TIC_LIMITE_CONSULTAS",
                    "Se alcanzó el límite de consultas. Espera un minuto e inténtalo nuevamente.");
        }
    }
}
