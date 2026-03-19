package com.example.chat.service;

import com.example.chat.domain.chat.ai.AiDto;
import com.example.chat.domain.chat.ChatType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// 🚨 유저님이 찾아내신 자막 추출 라이브러리 추가!
import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import io.github.thoroldvix.api.TranscriptContent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.annotation.PostConstruct;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;

    @Value("${alan.base-url:https://kdt-api-function.azurewebsites.net}")
    private String baseUrl;

    @Value("${alan.client-id:}")
    private String apiKey;

    @PostConstruct
    public void init() {
        if (baseUrl.endsWith("/api/v1")) baseUrl = baseUrl.replace("/api/v1", "");
        if (baseUrl.endsWith("/api")) baseUrl = baseUrl.replace("/api", "");
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️ AI API Key(client-id)가 설정되지 않았습니다.");
        } else {
            log.info("✅ AI API 클라이언트 로드 완료 (BaseUrl: {})", baseUrl);
        }
    }

    public AiDto.Response getAiAnswer(String content, String modelName, ChatType chatType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI 인증 정보가 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("client-id", apiKey);
        headers.set("x-client-id", apiKey);

        try {
            ResponseEntity<String> responseEntity;

            if (chatType == ChatType.CHAT) {
                // 일반 대화 (GET)
                String finalUrl = baseUrl + "/api/v1/question";

                String uri = UriComponentsBuilder.fromUriString(finalUrl)
                        .queryParam("question", content)
                        .queryParam("content", content)
                        .queryParam("model", modelName)
                        .queryParam("client_id", apiKey)
                        .build().encode().toUriString();

                log.info("🚀 AI 서버로 GET 요청을 보냅니다. URL: {}", uri);

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                responseEntity = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);

            } else {
                // 요약 및 유튜브 (POST)
                String endpoint = chatType == ChatType.SUMMARY
                        ? "/api/v1/chrome/page/summary"
                        : "/api/v1/summary-youtube";

                String finalUrl = baseUrl + endpoint;

                String uri = UriComponentsBuilder.fromUriString(finalUrl)
                        .queryParam("client_id", apiKey)
                        .build().encode().toUriString();

                log.info("🚀 AI 서버로 POST 요청을 보냅니다. URL: {}", uri);

                // String대신 Object를 받아 배열/리스트 형태의 JSON 구조도 담을 수 있게 변경
                Map<String, Object> requestMap = new HashMap<>();
                requestMap.put("model", modelName);
                requestMap.put("content", content);

                if (chatType == ChatType.SUMMARY) {
                    requestMap.put("body", content);
                } else if (chatType == ChatType.YOUTUBE) {
                    String videoId = extractVideoId(content);
                    List<Map<String, Object>> subtitleData = fetchYoutubeSubtitle(videoId);
                    requestMap.put("subtitle", subtitleData);
                }

                ObjectMapper om = new ObjectMapper();
                String jsonPayload = om.writeValueAsString(requestMap);

                HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

                responseEntity = restTemplate.exchange(uri, HttpMethod.POST, entity, String.class);
            }

            // ==========================================================
            // 만능 파싱 로직
            // ==========================================================
            String rawBody = responseEntity.getBody();
            log.info("📥 AI 원본 응답 데이터: {}", rawBody);

            if (rawBody == null || rawBody.isBlank()) {
                return new AiDto.Response("AI 응답이 비어있습니다.", 0);
            }

            try {
                ObjectMapper om = new ObjectMapper();
                JsonNode root = om.readTree(rawBody);

                if (root.isObject()) {
                    String answer = root.has("answer") ? root.get("answer").asText() :
                            root.has("content") ? root.get("content").asText() :
                                    root.has("message") ? root.get("message").asText() :
                                            root.has("text") ? root.get("text").asText() : rawBody;

                    int tokens = root.has("used_tokens") ? root.get("used_tokens").asInt() :
                            root.has("total_tokens") ? root.get("total_tokens").asInt() : 10;

                    return new AiDto.Response(answer, tokens);
                } else {
                    return new AiDto.Response(root.asText(), 10);
                }
            } catch (Exception e) {
                return new AiDto.Response(rawBody, 10);
            }

        } catch (Exception e) {
            log.error("AI API 호출 에러: {}", e.getMessage());
            throw new RuntimeException("[" + chatType.getDescription() + "] 호출 중 오류 발생: " + e.getMessage());
        }
    }

    public void clearAiHistory() {
        if (apiKey == null || apiKey.isBlank()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("client-id", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            String uri = UriComponentsBuilder.fromUriString(baseUrl + "/api/v1/reset-state")
                    .queryParam("client_id", apiKey)
                    .build().encode().toUriString();

            restTemplate.exchange(uri, HttpMethod.DELETE, entity, String.class);
            log.info("✅ AI 대화 문맥 초기화 성공");
        } catch (Exception e) {
            log.warn("AI 컨텍스트 초기화 실패 (무시됨): {}", e.getMessage());
        }
    }

    // =========================================================================
    // 유저님이 작성하신 유튜브 자막 추출 헬퍼 로직 (리스트/맵 구조로 Jackson 자동 JSON화 활용)
    // =========================================================================
    private String extractVideoId(String url) {
        try {
            if (url.contains("v=")) {
                int start = url.indexOf("v=") + 2;
                int end = url.indexOf("&", start);
                return end == -1 ? url.substring(start) : url.substring(start, end);
            } else if (url.contains("youtu.be/")) {
                int start = url.indexOf("youtu.be/") + 9;
                int end = url.indexOf("?", start);
                return end == -1 ? url.substring(start) : url.substring(start, end);
            }
        } catch (Exception e) {
            log.warn("유튜브 URL에서 ID 추출 실패: {}", url);
        }
        return url;
    }

    private List<Map<String, Object>> fetchYoutubeSubtitle(String videoId) {
        try {
            YoutubeTranscriptApi api = TranscriptApiFactory.createDefault();
            TranscriptContent content = api.getTranscript(videoId, "ko", "en");

            // Timestamp와 Content 추출
            List<Map<String, String>> textList = content.getContent().stream()
                    .map(f -> {
                        Map<String, String> item = new HashMap<>();
                        item.put("timestamp", formatTime(f.getStart()));
                        // 특수문자는 Jackson 라이브러리가 알아서 변환해주므로 개행 문자만 지움
                        item.put("content", f.getText() != null ? f.getText().replace("\n", " ").replace("\r", "") : "");
                        return item;
                    })
                    .toList();

            // FastAPI 포맷으로 래핑
            Map<String, Object> chapter = new HashMap<>();
            chapter.put("chapter_idx", 1);
            chapter.put("chapter_title", "Auto Generated");
            chapter.put("text", textList);

            return List.of(chapter);

        } catch (Exception e) {
            log.error("자막 추출 실패: {}", e.getMessage());
            // 실패하더라도 에러 방지를 위해 빈 배열 반환
            return Collections.emptyList();
        }
    }

    private String formatTime(double seconds) {
        return LocalTime.ofSecondOfDay((long) seconds % 86400)
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}