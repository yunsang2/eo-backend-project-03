package com.example.chat.domain.chat.ai;

public class AiDto {

    public record Request(
            // 사용할 모델
            String model,

            // 프롬프트 내용
            String content
    ) {}


    public record Response(
            // AI의 답변 텍스트
            String answer,

            // 사용된 토큰 수
            int used_tokens
    ) {}
}
