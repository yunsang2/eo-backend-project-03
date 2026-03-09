package com.example.chat.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Getter
@RequiredArgsConstructor
public enum ChatId {
    USER("ac"),
    PLAN("pl"),
    MODEL("ml"),
    PLANMODEL("pm"),
    RESET("rs"),
    CHATSESSION("cs"),
    CHATMESSAGE("mg"),
    PAYMENT("py");

    private final String key;

    public static String generateUUID(ChatId id) {
        return id.getKey() + "-" + UUID.randomUUID();
    }
}
