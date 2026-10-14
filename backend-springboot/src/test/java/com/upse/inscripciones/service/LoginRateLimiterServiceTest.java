package com.upse.inscripciones.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El bloqueo por intentos fallidos es la defensa contra fuerza bruta en
 * POST /api/auth/login: 5 fallos seguidos bloquean esa clave (IP+usuario).
 */
class LoginRateLimiterServiceTest {

    private final LoginRateLimiterService limiter = new LoginRateLimiterService();

    @Test
    void unaClaveSinIntentosNoEstaBloqueada() {
        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|ana")).isZero();
    }

    @Test
    void cuatroFallosTodaviaNoBloquean() {
        for (int i = 0; i < 4; i++) limiter.registrarIntentoFallido("1.1.1.1|ana");

        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|ana")).isZero();
    }

    @Test
    void elQuintoFalloBloqueaPorHastaQuinceMinutos() {
        for (int i = 0; i < 5; i++) limiter.registrarIntentoFallido("1.1.1.1|ana");

        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|ana")).isBetween(1L, 15L * 60);
    }

    @Test
    void unFalloMasDespuesDelBloqueoLoMantiene() {
        for (int i = 0; i < 7; i++) limiter.registrarIntentoFallido("1.1.1.1|ana");

        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|ana")).isPositive();
    }

    @Test
    void unLoginCorrectoLimpiaLosIntentosFallidos() {
        for (int i = 0; i < 4; i++) limiter.registrarIntentoFallido("1.1.1.1|ana");
        limiter.registrarIntentoExitoso("1.1.1.1|ana");
        limiter.registrarIntentoFallido("1.1.1.1|ana"); // vuelve a contar desde 1, no desde 5

        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|ana")).isZero();
    }

    @Test
    void unLoginCorrectoDesbloqueaUnaClaveBloqueada() {
        for (int i = 0; i < 5; i++) limiter.registrarIntentoFallido("1.1.1.1|ana");
        limiter.registrarIntentoExitoso("1.1.1.1|ana");

        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|ana")).isZero();
    }

    @Test
    void elBloqueoDeUnaClaveNoAfectaAOtras() {
        for (int i = 0; i < 5; i++) limiter.registrarIntentoFallido("1.1.1.1|ana");

        assertThat(limiter.segundosDeBloqueoRestantes("1.1.1.1|luis")).isZero();
        assertThat(limiter.segundosDeBloqueoRestantes("2.2.2.2|ana")).isZero();
    }
}
