package com.upse.inscripciones.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos de la documentación OpenAPI y el esquema de seguridad (JWT
 * Bearer) para que Swagger UI ofrezca el botón "Authorize". Solo existe
 * cuando la documentación está habilitada (SWAGGER_ENABLED=true).
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI sgcCecOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SGC-CEC API")
                        .version("v1")
                        .description("API del Sistema de Gestión del Centro de Educación Continua (UPSE). "
                                + "Inicia sesión en POST /api/auth/login y pega el token en \"Authorize\"."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
