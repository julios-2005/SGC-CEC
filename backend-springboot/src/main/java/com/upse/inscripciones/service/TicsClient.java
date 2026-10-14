package com.upse.inscripciones.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upse.inscripciones.config.TicsApiProperties;
import com.upse.inscripciones.exception.ConsultaInstitucionalException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Component
public class TicsClient {
    private final RestClient restClient;
    private final TicsApiProperties properties;
    private final ObjectMapper mapper;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    public TicsClient(@Qualifier("ticsRestClient") RestClient restClient,
                      TicsApiProperties properties, ObjectMapper mapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<JsonNode> consultar(String identificacion) {
        if (properties.getToken() == null || properties.getToken().isBlank()) {
            throw new ConsultaInstitucionalException(HttpStatus.SERVICE_UNAVAILABLE, "TIC_NO_CONFIGURADA",
                    "La consulta institucional no está configurada. Comunícate con el CEC.");
        }
        try {
            return restClient.get().uri("/buscarInformacionCEC/{documento}", identificacion)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.getToken().trim()))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            throw new ConsultaInstitucionalException(HttpStatus.SERVICE_UNAVAILABLE, "TIC_ACCESO_RECHAZADO",
                                    "El servicio institucional rechazó el acceso del CEC. Comunícate con el CEC.");
                        }
                        if (status == 429 || status >= 500) throw noDisponible();
                        // Un 404 puede ser una ruta errónea: no equivale a persona externa.
                        if (status != 200) throw respuestaInvalida();
                        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (body.length > MAX_RESPONSE_BYTES) throw respuestaInvalida();
                        JsonNode root;
                        try { root = mapper.readTree(body); }
                        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw respuestaInvalida(); }
                        if (root == null || !root.isArray()) throw respuestaInvalida();
                        List<JsonNode> result = new ArrayList<>();
                        for (JsonNode row : root) {
                            if (!row.isObject() || !row.path("identificacion").isTextual()
                                    || !identificacion.equalsIgnoreCase(row.path("identificacion").asText().trim())) {
                                throw respuestaInvalida();
                            }
                            result.add(row);
                        }
                        return result;
                    });
        } catch (ConsultaInstitucionalException ex) {
            throw ex;
        } catch (RestClientException ex) {
            // No conservar la causa: RestClient incluye la URL con la identificación.
            throw noDisponible();
        } catch (Exception ex) {
            throw respuestaInvalida();
        }
    }

    static ConsultaInstitucionalException respuestaInvalida() {
        return new ConsultaInstitucionalException(HttpStatus.BAD_GATEWAY, "TIC_RESPUESTA_INVALIDA",
                "No se pudo interpretar la información institucional. Comunícate con el CEC.");
    }

    private static ConsultaInstitucionalException noDisponible() {
        return new ConsultaInstitucionalException(HttpStatus.SERVICE_UNAVAILABLE, "TIC_NO_DISPONIBLE",
                "La consulta institucional no está disponible en este momento. Inténtalo nuevamente.");
    }
}
