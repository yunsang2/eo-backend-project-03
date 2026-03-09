package com.example.chat.domain.chat.message;

import com.example.chat.domain.BaseTimeEntity;
import com.example.chat.domain.chat.session.SessionEntity;
import com.example.chat.domain.ChatId;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "chat_messages")
public class MessageEntity extends BaseTimeEntity {

    @Id
    @Builder.Default
    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private String id = ChatId.generateUUID(ChatId.CHATMESSAGE);

    // 어떤 세션에 속한 메시지인지 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionEntity session;

    @Column(name = "role", nullable = false)
    private MessageRole role;

    // 대화 내용
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    // 모델명
    @Column(name = "modelName", nullable = false)
    private String modelName;

    // 소모된 토큰
    @Column(name = "usedTokens", nullable = false)
    private int usedTokens;
}