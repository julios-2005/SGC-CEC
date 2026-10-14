package com.upse.inscripciones.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Limita los intentos fallidos de login para frenar ataques de fuerza
 * bruta / adivinanza de contraseña sobre POST /api/auth/login.
 * <p>
 * Implementación en memoria (Caffeine), suficiente para el tamaño y
 * despliegue actual de esta aplicación (una sola instancia). Si en algún
 * momento se despliega en varias instancias detrás de un balanceador, esto
 * debe moverse a un almacén compartido (ej. Redis), porque cada instancia
 * llevaría su propio conteo.
 * <p>
 * Antes usaba un ConcurrentHashMap plano, que solo liberaba una entrada en
 * registrarIntentoExitoso() (login correcto): un intento fallido contra un
 * usuario que nunca llega a loguearse con éxito dejaba la entrada viva para
 * siempre (fuga de memoria de libro de texto si alguien apunta un scanner
 * al endpoint de login). Caffeine con expireAfterWrite resuelve esto solo,
 * sin lógica adicional: una entrada sin actividad nueva desaparece 15
 * minutos después de su última escritura (el mismo umbral que ya usa
 * DURACION_BLOQUEO), sin importar si el usuario llegó a loguearse o no.
 */
@Service
public class LoginRateLimiterService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private record Estado(int intentosFallidos, Instant bloqueadoHasta) {
    }

    private final Cache<String, Estado> intentosPorClave = Caffeine.newBuilder()
            .expireAfterWrite(DURACION_BLOQUEO)
            .build();

    /**
     * Segundos restantes de bloqueo para esta clave, o 0 si no está
     * bloqueada actualmente.
     */
    public long segundosDeBloqueoRestantes(String clave) {
        Estado estado = intentosPorClave.getIfPresent(clave);
        if (estado == null || estado.bloqueadoHasta() == null) {
            return 0;
        }
        Duration restante = Duration.between(Instant.now(), estado.bloqueadoHasta());
        return restante.isNegative() ? 0 : restante.toSeconds();
    }

    public void registrarIntentoFallido(String clave) {
        intentosPorClave.asMap().compute(clave, (k, actual) -> {
            int intentos = (actual == null ? 0 : actual.intentosFallidos()) + 1;
            Instant bloqueadoHasta = intentos >= MAX_INTENTOS_FALLIDOS
                    ? Instant.now().plus(DURACION_BLOQUEO)
                    : null;
            return new Estado(intentos, bloqueadoHasta);
        });
    }

    public void registrarIntentoExitoso(String clave) {
        intentosPorClave.invalidate(clave);
    }
}
