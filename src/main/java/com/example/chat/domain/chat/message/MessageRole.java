package com.example.chat.domain.chat.message;

public enum MessageRole {
    // 시스템 안내 메시지 (에러 등)
    SYSTEM,

    // 사용자가 입력한 질문
    USER,

    // AI가 답변한 내용
    ASSISTANT
}