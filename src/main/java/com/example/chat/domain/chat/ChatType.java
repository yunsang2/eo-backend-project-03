package com.example.chat.domain.chat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatType {
    // 3가지 기능으로 플랜 나누기
    CHAT("일반 대화"),

    // 페이지 요약과 번역을 동시에가능
    SUMMARY("웹 페이지 요약"),

    // 유튜브 요약
    YOUTUBE("유튜브 요약");

    private final String description;
}