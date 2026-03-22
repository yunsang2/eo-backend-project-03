package com.example.chat.domain.ai;

import lombok.*;
import java.util.List;
import java.util.Map;

public class AlanAiDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class PageTranslateRequest {
        private List<String> contents;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class YoutubeSubtitleRequest {
        private List<Map<String, Object>> subtitle;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class YoutubeSubtitleResponse {
        private String answer;
        private int used_tokens;
    }
}