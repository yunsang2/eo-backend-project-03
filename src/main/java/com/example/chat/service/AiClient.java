package com.example.chat.service;

import com.example.chat.domain.chat.ai.AiDto;
import com.example.chat.domain.chat.ChatType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

/**
 * 외부 AI 서버와 통신하는 클라이언트입니다.
 * 설정 파일(yml)에서 API 키와 베이스 URL을 주입받습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;


    @Value("${alan.api.base-url:https://kdt-api-function.azurewebsites.net/api}")
    private String baseUrl;


    @Value("${alan.client-id:}")
    private String apiKey;

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("AI API Key가 설정되지 않았습니다. application-secret.yml을 확인하세요.");
        } else {
            log.info("AI API 클라이언트가 로드되었습니다. (Endpoint: {})", baseUrl);
        }
    }

    public AiDto.Response getAiAnswer(String content, String modelName, ChatType chatType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI API 키가 없습니다. 설정 파일을 확인해주세요.");
        }

        String endpoint = switch (chatType) {
            case CHAT -> "/chat";
            case SUMMARY -> "/summary";
            case YOUTUBE -> "/youtube";
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        AiDto.Request requestBody = new AiDto.Request(modelName, content);
        HttpEntity<AiDto.Request> entity = new HttpEntity<>(requestBody, headers);

        try {
            // baseUrl + endpoint 조합으로 호출
            return restTemplate.postForObject(baseUrl + endpoint, entity, AiDto.Response.class);
        } catch (Exception e) {
            log.error("AI API 호출 에러: {}", e.getMessage());
            throw new RuntimeException("[" + chatType.getDescription() + "] 호출 중 오류 발생: " + e.getMessage());
        }
    }

    public void clearAiHistory() {
        if (apiKey == null || apiKey.isBlank()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            restTemplate.postForEntity(baseUrl + "/chat/clear", entity, String.class);
        } catch (Exception e) {
            log.warn("AI 컨텍스트 초기화 실패 (무시됨)");
        }
    }
}