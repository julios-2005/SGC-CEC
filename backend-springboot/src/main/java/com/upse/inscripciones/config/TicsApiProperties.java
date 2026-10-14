package com.upse.inscripciones.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.tics")
public class TicsApiProperties {
    private String baseUrl = "https://sga.upse.edu.ec/restsiia/api/public";
    private String token = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(8);
    // Coincidencias exactas, ignorando acentos, mayúsculas y separadores.
    // Ampliar con los valores oficiales de TIC; nunca inferir un cargo por exclusión.
    private List<String> tiposDocente = List.of("ACADEMICO","DOCENTE", "DOCENTE UPSE", "PERSONAL ACADEMICO");
    private List<String> tiposAdministrativo = List.of("ADMINISTRATIVO", "ADMINISTRATIVO UPSE", "PERSONAL ADMINISTRATIVO");
    private List<String> cargosDocente = List.of("DOCENTE", "PROFESOR", "PROFESORA");
    private List<String> cargosAdministrativo = List.of("ANALISTA 2 DE DESARROLLO DE SISTEMAS");
    private int consultasPorMinuto = 30;
}
