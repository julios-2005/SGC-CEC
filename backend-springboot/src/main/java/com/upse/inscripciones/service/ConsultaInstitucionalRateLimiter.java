package com.upse.inscripciones.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.upse.inscripciones.config.TicsApiProperties;
import com.upse.inscripciones.exception.ConsultaInstitucionalException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita las consultas públicas a la API institucional (TIC) en tres ventanas fijas:
 * por IP y minuto, por IP y día, y por documento y hora. Las dos últimas frenan el
 * barrido de muchas cédulas (enumeración) que un tope de un minuto no detiene.
 */
@Service
public class ConsultaInstitucionalRateLimiter {
    private final Cache<String, AtomicInteger> porMinuto = ventana(Duration.ofMinutes(1), 10_000);
    private final Cache<String, AtomicInteger> porDia = ventana(Duration.ofHours(24), 50_000);
    private final Cache<String, AtomicInteger> porDocumento = ventana(Duration.ofHours(1), 50_000);
    private final TicsApiProperties properties;

    public ConsultaInstitucionalRateLimiter(TicsApiProperties properties) { this.properties = properties; }

    private static Cache<String, AtomicInteger> ventana(Duration duracion, long maximo) {
        return Caffeine.newBuilder().maximumSize(maximo).expireAfterWrite(duracion).build();
    }

    /** Solo por IP y minuto (compatibilidad). Preferir {@link #comprobar(String, String)}. */
    public void comprobar(String ip) {
        exceder(porMinuto, ip == null ? "desconocida" : ip, properties.getConsultasPorMinuto());
    }

    public void comprobar(String ip, String documento) {
        String clave = ip == null ? "desconocida" : ip;
        exceder(porMinuto, clave, properties.getConsultasPorMinuto());
        exceder(porDia, clave, properties.getConsultasPorDia());
        if (documento != null && !documento.isBlank()) {
            exceder(porDocumento, documento.trim().toLowerCase(), properties.getConsultasPorDocumentoHora());
        }
    }

    private static void exceder(Cache<String, AtomicInteger> cache, String clave, int maximo) {
        if (cache.get(clave, k -> new AtomicInteger()).incrementAndGet() > Math.max(1, maximo)) {
            throw new ConsultaInstitucionalException(HttpStatus.TOO_MANY_REQUESTS, "TIC_LIMITE_CONSULTAS",
                    "Se alcanzó el límite de consultas. Inténtalo nuevamente más tarde.");
        }
    }
}
