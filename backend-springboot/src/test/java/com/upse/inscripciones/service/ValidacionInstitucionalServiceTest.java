package com.upse.inscripciones.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upse.inscripciones.config.TicsApiProperties;
import com.upse.inscripciones.entity.TipoUsuario;
import com.upse.inscripciones.exception.ConsultaInstitucionalException;
import com.upse.inscripciones.exception.ValidacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.upse.inscripciones.entity.TipoUsuario.*;

class ValidacionInstitucionalServiceTest {
    private TicsClient client;
    private TicsApiProperties config;
    private ValidacionInstitucionalService service;

    @BeforeEach void setup() {
        client = mock(TicsClient.class);
        config = new TicsApiProperties();
        service = new ValidacionInstitucionalService(client, config);
    }
    private JsonNode row(String data) throws Exception { return new ObjectMapper().readTree(data); }
    private void response(String data) throws Exception { when(client.consultar("1234567890")).thenReturn(List.of(row(data))); }

    @Test void estudianteYPosgradoRequierenMatriculaActivaYNoSeExcluyen() throws Exception {
        response("{\"estudianteGrado\":true,\"matriculaGradoActiva\":true,\"estudiantePostgrado\":true,\"matriculaPostgradoActiva\":true}");
        assertThat(service.validar("1234567890", ESTUDIANTE_UPSE).verificado()).isTrue();
        assertThat(service.validar("1234567890", MAESTRANDO).verificado()).isTrue();
        response("{\"estudianteGrado\":true,\"matriculaGradoActiva\":false}");
        assertThat(service.validar("1234567890", ESTUDIANTE_UPSE).verificado()).isFalse();
    }

    @Test void unCampoFaltanteNoSeInterpretaComoFalso() throws Exception {
        response("{\"estudianteGrado\":false}");
        assertThatThrownBy(() -> service.validar("1234567890", ESTUDIANTE_UPSE))
                .isInstanceOf(ConsultaInstitucionalException.class);
    }

    @Test void reconocePersonalAdministrativoDelContratoObservado() throws Exception {
        response("{\"tipoUsuario\":\"Personal upse\",\"cargo\":\"ANALISTA 2 DE DESARROLLO DE SISTEMAS\"}");
        assertThat(service.validar("1234567890", ADMINISTRATIVO_UPSE).verificado()).isTrue();
        assertThat(service.validar("1234567890", DOCENTE_UPSE).verificado()).isFalse();
    }

    @Test void reconoceDocenteSinSuponerQueTodoPersonalEsAdministrativo() throws Exception {
        response("{\"tipoUsuario\":\"Personal upse\",\"cargo\":\"DOCENTE\"}");
        assertThat(service.validar("1234567890", DOCENTE_UPSE).verificado()).isTrue();
        assertThat(service.validar("1234567890", ADMINISTRATIVO_UPSE).verificado()).isFalse();
        response("{\"tipoUsuario\":\"Personal upse\",\"cargo\":\"CARGO SIN CLASIFICAR\"}");
        assertThat(service.validar("1234567890", ADMINISTRATIVO_UPSE).verificado()).isFalse();
    }

    @Test void catalogosSonConfigurablesYNormalizanSeparadoresYAcentos() throws Exception {
        config.setCargosDocente(List.of("PROFESOR TITULAR"));
        response("{\"tipoUsuario\":\"PERSONAL_UPSE\",\"cargo\":\"  profesór titular  \"}");
        assertThat(service.validar("1234567890", DOCENTE_UPSE).verificado()).isTrue();
    }

    @Test void tipoLaboralExplicitoNoNecesitaCargo() throws Exception {
        response("{\"tipoUsuario\":\"DOCENTE_UPSE\"}");
        assertThat(service.validar("1234567890", DOCENTE_UPSE).verificado()).isTrue();
        response("{\"tipoUsuario\":\"ADMINISTRATIVO_UPSE\"}");
        assertThat(service.validar("1234567890", ADMINISTRATIVO_UPSE).verificado()).isTrue();
    }

    @Test void discapacidadSeRevisaConElCarnetYNuncaConsultaATic() {
        var respuesta = service.validar("1234567890", PERSONA_DISCAPACIDAD);
        assertThat(respuesta.requiereConsulta()).isFalse();
        assertThat(respuesta.verificado()).isFalse();
        assertThat(respuesta.estado()).isEqualTo("REVISION_DOCUMENTAL");
        // No bloquea: quien no consta en la base de la universidad se inscribe con su carnet.
        service.validarParaInscripcion("1234567890", PERSONA_DISCAPACIDAD);
        verifyNoInteractions(client);
    }

    @Test void unEstudianteConDiscapacidadSeVerificaComoEstudianteSinImportarElIndicadorDeDiscapacidad() throws Exception {
        response("{\"estudianteGrado\":true,\"matriculaGradoActiva\":true,\"discapacidad\":1}");
        assertThat(service.validar("1234567890", ESTUDIANTE_UPSE).verificado()).isTrue();
    }

    @Test void agregaRegistrosDeLaMismaPersona() throws Exception {
        when(client.consultar("1234567890")).thenReturn(List.of(
                row("{\"estudianteGrado\":false,\"matriculaGradoActiva\":false}"),
                row("{\"estudianteGrado\":true,\"matriculaGradoActiva\":true}")));
        assertThat(service.validar("1234567890", ESTUDIANTE_UPSE).verificado()).isTrue();
    }

    @Test void externoYDiscapacidadConservanElFlujoSinConsultarATic() {
        for (TipoUsuario tipo : List.of(EXTERNO, PERSONA_DISCAPACIDAD)) {
            assertThat(service.validar("1234567890", tipo).requiereConsulta()).isFalse();
            service.validarParaInscripcion("1234567890", tipo);
        }
        verifyNoInteractions(client);
    }

    @Test void graduadoSeConfirmaConTicPorSuTipoDeUsuario() throws Exception {
        assertThat(ValidacionInstitucionalService.requiereConsulta(GRADUADO)).isTrue();
        response("{\"tipoUsuario\":\"Graduado\"}");
        var respuesta = service.validar("1234567890", GRADUADO);
        assertThat(respuesta.requiereConsulta()).isTrue();
        assertThat(respuesta.verificado()).isTrue();
        assertThat(respuesta.estado()).isEqualTo("CONFIRMADO");
        service.validarParaInscripcion("1234567890", GRADUADO);
    }

    @Test void graduadoNoSeDeduceDeOtrosTiposNiDeUnaMatriculaInactiva() throws Exception {
        response("{\"tipoUsuario\":\"Personal upse\",\"estudianteGrado\":true,\"matriculaGradoActiva\":false}");
        assertThat(service.validar("1234567890", GRADUADO).verificado()).isFalse();
        assertThatThrownBy(() -> service.validarParaInscripcion("1234567890", GRADUADO))
                .isInstanceOf(ValidacionException.class);
    }

    @Test void graduadoSinRegistroEnTicNoSePuedeInscribir() throws Exception {
        when(client.consultar("1234567890")).thenReturn(List.of());
        assertThat(service.validar("1234567890", GRADUADO).estado()).isEqualTo("SIN_REGISTRO");
        assertThatThrownBy(() -> service.validarParaInscripcion("1234567890", GRADUADO))
                .isInstanceOf(ValidacionException.class);
    }

    @Test void elValorDeGraduadoEsConfigurableYNormalizaAcentosYSeparadores() throws Exception {
        config.setTiposGraduado(List.of("EGRESADO TITULADO"));
        response("{\"tipoUsuario\":\"egresado_titulado\"}");
        assertThat(service.validar("1234567890", GRADUADO).verificado()).isTrue();
        response("{\"tipoUsuario\":\"Graduado\"}");
        assertThat(service.validar("1234567890", GRADUADO).verificado()).isFalse();
    }

    @Test void graduadoSinTipoDeUsuarioEnLaRespuestaEsUnFormatoInesperado() throws Exception {
        response("{\"estudianteGrado\":false}");
        assertThatThrownBy(() -> service.validar("1234567890", GRADUADO))
                .isInstanceOf(ConsultaInstitucionalException.class);
    }

    @Test void sinRegistroNoConfirmaCategoriaYBloqueaInscripcion() {
        when(client.consultar("1234567890")).thenReturn(List.of());
        assertThat(service.validar("1234567890", DOCENTE_UPSE).estado()).isEqualTo("SIN_REGISTRO");
        assertThatThrownBy(() -> service.validarParaInscripcion("1234567890", DOCENTE_UPSE)).isInstanceOf(ValidacionException.class);
    }

    @Test void documentoInvalidoNoLlegaATic() {
        assertThatThrownBy(() -> service.validar("../", DOCENTE_UPSE)).isInstanceOf(ValidacionException.class);
        verifyNoInteractions(client);
    }

    @Test void limitaConsultasPorIp() {
        config.setConsultasPorMinuto(2);
        var limiter = new ConsultaInstitucionalRateLimiter(config);
        limiter.comprobar("ip-a"); limiter.comprobar("ip-a");
        assertThatThrownBy(() -> limiter.comprobar("ip-a")).isInstanceOf(ConsultaInstitucionalException.class);
        assertThatCode(() -> limiter.comprobar("ip-b")).doesNotThrowAnyException();
    }

    @Test void limitaConsultasPorDiaYPorIp() {
        config.setConsultasPorMinuto(100);
        config.setConsultasPorDia(3);
        var limiter = new ConsultaInstitucionalRateLimiter(config);
        for (int i = 0; i < 3; i++) limiter.comprobar("ip-a", "100000000" + i);
        assertThatThrownBy(() -> limiter.comprobar("ip-a", "1000000009")).isInstanceOf(ConsultaInstitucionalException.class);
        assertThatCode(() -> limiter.comprobar("ip-b", "1000000009")).doesNotThrowAnyException();
    }

    @Test void limitaConsultasDeUnMismoDocumentoDesdeCualquierIp() {
        config.setConsultasPorDocumentoHora(2);
        var limiter = new ConsultaInstitucionalRateLimiter(config);
        limiter.comprobar("ip-a", "1234567890"); limiter.comprobar("ip-b", "1234567890");
        assertThatThrownBy(() -> limiter.comprobar("ip-c", "1234567890")).isInstanceOf(ConsultaInstitucionalException.class);
        assertThatCode(() -> limiter.comprobar("ip-c", "0987654321")).doesNotThrowAnyException();
    }
}
