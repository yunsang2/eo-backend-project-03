package com.example.chat.domain.chat.session;

import com.example.chat.domain.BaseTimeEntity;
import com.example.chat.domain.chat.message.MessageEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.ChatId;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "chat_sessions")
public class SessionEntity extends BaseTimeEntity {

    @Id
    @Builder.Default
    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private String id = ChatId.generateUUID(ChatId.CHATSESSION);

    // 어떤 유저의 채팅방인지 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "title", nullable = false)
    private String title;

    // 세션이 지워지면 안에 있는 메시지도 다 날아가도록 Cascade 설정
    @Builder.Default
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MessageEntity> messages = new ArrayList<>();
}