package com.upse.inscripciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;

@Configuration
public class TicsApiConfig {
    @Bean("ticsRestClient")
    public RestClient ticsRestClient(RestClient.Builder builder, TicsApiProperties properties) {
        URI uri = URI.create(properties.getBaseUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("TICS_API_BASE_URL debe ser una URL HTTPS sin credenciales ni parámetros.");
        }
        var http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.getReadTimeout());
        return builder.baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
    }
}
