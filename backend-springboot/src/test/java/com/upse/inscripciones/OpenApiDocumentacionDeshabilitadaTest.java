package com.upse.inscripciones;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Por defecto (producción) la documentación OpenAPI NO se expone: ni la
 * especificación ni Swagger UI son accesibles sin sesión.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openapi-deshabilitada;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "app.jwt.secret=clave-aislada-de-prueba-nunca-usar-en-produccion-1234567890",
        "app.cors.allowed-origins=http://localhost:4200",
        "spring.mail.host=localhost", "spring.mail.port=1",
        "spring.mail.username=prueba@example.test", "spring.mail.password=solo-prueba"
})
@AutoConfigureMockMvc
class OpenApiDocumentacionDeshabilitadaTest {

    @org.junit.jupiter.api.io.TempDir
    static Path testUploads;

    @Autowired MockMvc http;

    @DynamicPropertySource
    static void isolatedFiles(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", () -> testUploads.toString());
    }

    @Test
    void sinSwaggerEnabledLaEspecificacionNoEsAccesibleSinSesion() throws Exception {
        http.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void sinSwaggerEnabledSwaggerUiNoEsAccesibleSinSesion() throws Exception {
        http.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        http.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }
}
