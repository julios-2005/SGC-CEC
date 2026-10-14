package com.upse.inscripciones.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upse.inscripciones.config.TicsApiProperties;
import com.upse.inscripciones.exception.ConsultaInstitucionalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TicsClientTest {
    private TicsClient client;
    private MockRestServiceServer server;
    private TicsApiProperties config;
    private static final String URL = "https://tic.example.test/buscarInformacionCEC/1234567890";

    @BeforeEach void setup() {
        config = new TicsApiProperties();
        config.setToken("token-ficticio-de-prueba");
        var builder = RestClient.builder().baseUrl("https://tic.example.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TicsClient(builder.build(), config, new ObjectMapper());
    }

    @Test void enviaGetYBearerDesdeElBackend() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-ficticio-de-prueba"))
                .andExpect(header("Accept", "application/json"))
                .andRespond(withSuccess("[{\"identificacion\":\"1234567890\",\"estudianteGrado\":true}]", MediaType.APPLICATION_JSON));
        assertThat(client.consultar("1234567890")).hasSize(1);
        server.verify();
    }

    @Test void arregloVacioEsUnaConsultaSinRegistro() {
        server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(client.consultar("1234567890")).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 500, 503})
    void erroresDeTicNoSon401DelLoginNiPersonasExternas(int code) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(code)).body("respuesta privada"));
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOfSatisfying(ConsultaInstitucionalException.class,
                e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).doesNotContain("respuesta privada", "token-ficticio", "1234567890");
                });
    }

    @ParameterizedTest @ValueSource(strings = {"null", "{}", "no es json", "[{}]", "[{\"identificacion\":\"9999999999\"}]"})
    void rechazaRespuestasInvalidasODeOtraPersona(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOfSatisfying(ConsultaInstitucionalException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test void noConvierteUn404EnAusenciaDePersona() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOfSatisfying(ConsultaInstitucionalException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test void noSigueRedireccionesNiReenviaElToken() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://otro.example.test"));
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOf(ConsultaInstitucionalException.class);
        server.verify();
    }

    @Test void timeoutEsUnFalloTemporal() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOfSatisfying(ConsultaInstitucionalException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("TIC_NO_DISPONIBLE"));
    }

    @Test void rechazaUnCuerpoExcesivo() {
        server.expect(requestTo(URL)).andRespond(withSuccess(" ".repeat(262145), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOf(ConsultaInstitucionalException.class);
    }

    @Test void sinTokenNoHacePeticiones() {
        config.setToken("");
        assertThatThrownBy(() -> client.consultar("1234567890")).isInstanceOfSatisfying(ConsultaInstitucionalException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("TIC_NO_CONFIGURADA"));
        server.verify();
    }
}
