package com.example.chat.domain.chat.session;

import com.example.chat.domain.chat.ChatType;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class SessionDto {

    // 채팅방 생성 요청
    public record CreateRequest(
            @NotBlank(message = "채팅방 제목을 입력해주세요.")
            String title
    ) {}

    // 채팅방 목록/단일 응답
    public record Response(
            String id,
            String title,
            ChatType chatType,
            LocalDateTime createdAt
    ) {
        public static Response fromEntity(SessionEntity session) {
            return new Response(
                    session.getId(),
                    session.getTitle(),
                    session.getChatType(),
                    session.getCreatedAt()
            );
        }
    }
}

