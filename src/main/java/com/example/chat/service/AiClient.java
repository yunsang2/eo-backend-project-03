package com.example.chat.service;

import com.example.chat.domain.chat.ChatType;
import com.example.chat.domain.chat.ai.AiDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class AiClient {

    private final WebClient alanWebClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${alan.client-id}") // application-secret.yml의 client-id와 매칭
    private String clientId;

    // WebClient 빈 주입 에러 방지용 직접 초기화
    public AiClient(@Value("${alan.base-url:https://kdt-api-function.azurewebsites.net/api/v1}") String baseUrl) {
        this.alanWebClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * [ChatService 연동 핵심 메서드]
     * ChatService.askAi() 에서 넘겨주는 프롬프트, 모델명, 채팅 타입을 그대로 받아서 처리합니다.
     */
    public AiDto.Response getAiAnswer(String content, String modelName, ChatType chatType) {
        try {
            // ChatType에 따라 알맞은 외부 API 엔드포인트로 분기합니다.
            String rawResponse = switch (chatType) {
                case CHAT -> askQuestion(content, modelName);
                case SUMMARY -> summarizePage(content);
                case YOUTUBE -> askYoutubeSummary(content);
            };

            // 외부 API의 다양한 JSON 응답 규격을 하나로 통일하여 파싱합니다.
            return parseResponse(rawResponse);

        } catch (Exception e) {
            log.error("AI 호출 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("[" + chatType.getDescription() + "] API 호출 실패: " + e.getMessage());
        }
    }

    /**
     * [ChatService 연동 메서드]
     * 새로운 세션이 생성될 때 ChatService.prepareChat() 에서 호출하여 이전 문맥을 비웁니다.
     */
    public void clearAiHistory() {
        log.info("앨런 AI 상태 초기화 요청 (client_id: {})", clientId);
        try {
            alanWebClient.method(HttpMethod.DELETE)
                    .uri("/reset-state")
                    .bodyValue(Map.of("client_id", clientId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.warn("상태 초기화 중 오류 발생 (무시): {}", e.getMessage());
        }
    }

    // =========================================================================
    // 외부 API(Alan AI 등)와 직접 통신하는 내부 헬퍼 메서드
    // =========================================================================

    /**
     * 일반 질문 (GET /api/v1/question)
     */
    private String askQuestion(String content, String modelName) {
        log.info("앨런 일반 질문 요청 (모델: {}): {}", modelName, content);
        return alanWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/question")
                        .queryParam("client_id", clientId)
                        .queryParam("content", content)
                        // 외부 API가 지원한다면 모델명도 추가
                        .queryParam("model", modelName)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 페이지 요약 (POST /api/v1/chrome/page/summary)
     */
    private String summarizePage(String content) {
        log.info("앨런 페이지 요약 요청 (길이: {})", content.length());
        return alanWebClient.post()
                .uri("/chrome/page/summary")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", content))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 유튜브 요약 (POST /api/v1/summary-youtube)
     */
    private String askYoutubeSummary(String videoUrl) {
        log.info("앨런 유튜브 요약 요청 URL: {}", videoUrl);

        // 영상 URL에서 Video ID만 깔끔하게 추출 (정규식 적용됨)
        String videoId = extractVideoId(videoUrl);

        // 자바 서버에서 직접 유튜브 자막 추출
        List<Map<String, Object>> subtitleData = fetchYoutubeSubtitle(videoId);

        // 추출한 자막을 AI 요약 서버로 전송
        return alanWebClient.post()
                .uri("/summary-youtube")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("subtitle", subtitleData))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // --- JSON 응답 파싱 (안전 장치) ---
    private AiDto.Response parseResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            // 'answer' 필드가 있으면 쓰고, 없으면 'content' 필드, 둘 다 없으면 통째로 반환
            String answer = root.has("answer") ? root.get("answer").asText() :
                    root.has("content") ? root.get("content").asText() : rawBody;

            // 토큰 정보가 없으면 기본값 10으로 처리
            int tokens = root.has("used_tokens") ? root.get("used_tokens").asInt() : 10;
            return new AiDto.Response(answer, tokens);
        } catch (Exception e) {
            return new AiDto.Response(rawBody, 10);
        }
    }

    // --- 유튜브 자막 추출 헬퍼 ---
    private String extractVideoId(String text) {
        if (text == null) return "";

        //  정규식을 사용하여 프롬프트가 섞여 있어도 v= 뒤의 '정확히 11자리' 영상 ID만 추출
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:v=|youtu\\.be\\/)([a-zA-Z0-9_-]{11})").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 만약 정규식에 안 걸리면 기존 방식대로 자르되, 특수문자(줄바꿈, 따옴표 등)를 제거
        String url = text;
        if (url.contains("v=")) {
            url = url.split("v=")[1].split("&")[0];
        } else if (url.contains("youtu.be/")) {
            url = url.split("youtu.be/")[1].split("\\?")[0];
        }

        // 영어 대소문자, 숫자, 언더바(_), 하이픈(-) 만 남기고 모두 제거
        return url.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private List<Map<String, Object>> fetchYoutubeSubtitle(String videoId) {
        try {

            YoutubeTranscriptApi api = TranscriptApiFactory.createDefault();

            // "ko" (한국어) 우선, 없으면 "en" (영어) 자막 추출
            TranscriptContent content = api.getTranscript(videoId, "ko", "en");

            List<Map<String, String>> textList = content.getContent().stream()
                    .map(f -> {
                        Map<String, String> item = new HashMap<>();
                        // 시작 시간을 HH:mm:ss 포맷으로 변환
                        item.put("timestamp", LocalTime.ofSecondOfDay((long) f.getStart() % 86400).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                        item.put("content", f.getText().replace("\n", " "));
                        return item;
                    }).toList();

            return List.of(Map.of(
                    "chapter_idx", 1,
                    "chapter_title", "동영상 내용",
                    "text", textList
            ));
        } catch (Exception e) {
            log.error("유튜브 자막 추출 실패 (videoId: {}): {}", videoId, e.getMessage());
            // 자막 추출 실패 시 빈 리스트를 반환하여 AI가 에러를 내지 않도록 처리
            return Collections.emptyList();
        }
    }
}