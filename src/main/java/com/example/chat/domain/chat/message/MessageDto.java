package com.example.chat.domain.chat.message;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class MessageDto {

    // AI에게 질문 보내기 요청
    public record Request(
            @NotBlank(message = "질문 내용을 입력해주세요.")
            String content,

            // 기본값 gpt-4o-mini
            @NotBlank(message = "사용할 AI 모델을 선택해주세요.")
            String modelName
    ) {}

    // 채팅 메시지 내역 응답 (질문/답변 공통)
    public record Response(
            String id,
            String sessionId,
            String role,
            String content,
            String modelName,
            int usedTokens,
            LocalDateTime createdAt
    ) {
        public static Response fromEntity(MessageEntity message) {
            return new Response(
                    message.getId(),
                    message.getSession() != null ? message.getSession().getId() : null,
                    message.getRole().name(),
                    message.getContent(),
                    message.getModelName(),
                    message.getUsedTokens(),
                    message.getCreatedAt()
            );
        }
    }
}