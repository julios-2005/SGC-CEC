package com.upse.inscripciones.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.upse.inscripciones.config.TicsApiProperties;
import com.upse.inscripciones.dto.ValidacionInstitucionalResponse;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.exception.ValidacionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ValidacionInstitucionalService {
    // Categorías que se confirman con TIC (incluye Graduado, que TIC identifica con
    // el valor de `tipoUsuario` configurado en app.tics.tipos-graduado).
    // Persona con discapacidad NO está aquí a propósito: la discapacidad se revisa
    // siempre con el carnet adjunto. Así quien es Estudiante UPSE y además tiene
    // discapacidad elige UNA categoría (la más favorable) y quien no consta en la
    // base de la universidad puede acreditar su discapacidad con el documento.
    private static final Set<TipoUsuario> TIPOS_TIC = Set.of(
            TipoUsuario.ESTUDIANTE_UPSE,
            TipoUsuario.DOCENTE_UPSE,
            TipoUsuario.ADMINISTRATIVO_UPSE,
            TipoUsuario.MAESTRANDO,
            TipoUsuario.GRADUADO);
    private static final Set<TipoUsuario> TIPOS_DOCUMENTALES = Set.of(
            TipoUsuario.PERSONA_DISCAPACIDAD);
    private final TicsClient client;
    private final TicsApiProperties properties;

    public static boolean requiereConsulta(TipoUsuario tipo) {
        return tipo != null && TIPOS_TIC.contains(tipo);
    }

    public ValidacionInstitucionalResponse validar(String identificacion, TipoUsuario tipo) {
        if (tipo == null || identificacion == null || !identificacion.trim().matches("[A-Za-z0-9]{5,20}")) {
            throw new ValidacionException("Selecciona el tipo de participante e ingresa un documento válido.");
        }
        if (!requiereConsulta(tipo)) {
            if (tipo == TipoUsuario.PERSONA_DISCAPACIDAD) {
                return new ValidacionInstitucionalResponse(
                        tipo,
                        false,
                        false,
                        "REVISION_DOCUMENTAL",
                        "La condición de discapacidad se revisa con el documento de respaldo.");
            }

            return new ValidacionInstitucionalResponse(
                    tipo,
                    false,
                    false,
                    "NO_REQUERIDA",
                    "La categoría Externo conserva el proceso habitual de inscripción.");
        }
        List<JsonNode> rows = client.consultar(identificacion.trim());
        if (rows.isEmpty()) {
            return new ValidacionInstitucionalResponse(tipo, true, false, "SIN_REGISTRO",
                    "La consulta no encontró un registro para confirmar esta categoría. Comunícate con el CEC si necesitas revisión.");
        }
        // Se validan todos los registros de esa misma identificación, sin suponer que
        // siempre llega uno solo.
        boolean confirmado = false;
        for (JsonNode row : rows)
            confirmado |= confirma(row, tipo);
        return new ValidacionInstitucionalResponse(tipo, true, confirmado,
                confirmado ? "CONFIRMADO" : "NO_CONFIRMADO",
                confirmado ? "La categoría seleccionada fue confirmada por la información institucional."
                        : "La información institucional no confirma la categoría seleccionada. Revisa tu selección o comunícate con el CEC.");
    }

    /**
     * Se llama al guardar, antes de descontar cupos o escribir archivos; no confía
     * en el navegador.
     */
    public void validarParaInscripcion(String identificacion, TipoUsuario tipo) {
        if (requiereConsulta(tipo)) {
            var respuesta = validar(identificacion, tipo);
            if (!respuesta.verificado())
                throw new ValidacionException(respuesta.mensaje());
        }
    }

    private boolean confirma(JsonNode row, TipoUsuario tipo) {
        return switch (tipo) {
            case ESTUDIANTE_UPSE -> bool(row, "estudianteGrado") & bool(row, "matriculaGradoActiva");
            case MAESTRANDO -> bool(row, "estudiantePostgrado") & bool(row, "matriculaPostgradoActiva");
            case DOCENTE_UPSE -> esPersonal(row, true);
            case ADMINISTRATIVO_UPSE -> esPersonal(row, false);
            case GRADUADO -> esGraduado(row);
            default -> false;
        };
    }

    private boolean esGraduado(JsonNode row) {
        JsonNode tipoApi = row.path("tipoUsuario");

        if (!tipoApi.isTextual()) {
            throw TicsClient.respuestaInvalida();
        }

        return contiene(properties.getTiposGraduado(), normalizar(tipoApi.asText()));
    }

    private boolean esPersonal(JsonNode row, boolean docente) {
        if (!row.path("tipoUsuario").isTextual())
            throw TicsClient.respuestaInvalida();
        String tipo = normalizar(row.path("tipoUsuario").asText());
        List<String> tipos = docente ? properties.getTiposDocente() : properties.getTiposAdministrativo();
        if (contiene(tipos, tipo))
            return true;
        if (!tipo.equals("PERSONAL UPSE"))
            return false;
        JsonNode cargo = row.path("cargo");
        if (!cargo.isTextual())
            return false;
        return contiene(docente ? properties.getCargosDocente() : properties.getCargosAdministrativo(),
                normalizar(cargo.asText()));
    }

    private boolean bool(JsonNode row, String key) {
        JsonNode value = row.path(key);
        if (!value.isBoolean())
            throw TicsClient.respuestaInvalida();
        return value.booleanValue();
    }

    private boolean contiene(List<String> values, String value) {
        return values != null
                && values.stream().map(ValidacionInstitucionalService::normalizar).anyMatch(value::equals);
    }

    private static String normalizar(String value) {
        if (value == null)
            return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
    }
}
