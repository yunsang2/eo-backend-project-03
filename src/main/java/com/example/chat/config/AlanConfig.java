package com.example.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AlanConfig {

    @Value("${alan.base-url:https://kdt-api-function.azurewebsites.net/api/v1}")
    private String baseUrl;

    @Bean
    public WebClient alanWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}