package com.example.chat.service;

import com.example.chat.domain.chat.ai.AiDto; // 👈 위에서 만든 AiDto 임포트
import com.example.chat.domain.chat.ChatType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String BASE_URL = "https://kdt-api-function.azurewebsites.net/api";

    @Value("${alan.api.key}")
    private String apiKey;

    public AiDto.Response getAiAnswer(String content, String modelName, ChatType chatType) {
        String endpoint = switch (chatType) {
            case CHAT -> "/chat";
            case SUMMARY -> "/summary";
            case YOUTUBE -> "/youtube";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        // AiDto.Request 사용
        AiDto.Request requestBody = new AiDto.Request(modelName, content);
        HttpEntity<AiDto.Request> entity = new HttpEntity<>(requestBody, headers);

        // AiDto.Response 사용
        return restTemplate.postForObject(BASE_URL + endpoint, entity, AiDto.Response.class);
    }
}