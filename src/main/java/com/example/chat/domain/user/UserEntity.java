package com.example.chat.domain.user;


import com.example.chat.domain.BaseTimeEntity;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.ChatId;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "users")
public class UserEntity extends BaseTimeEntity {

    @Id
    @Builder.Default
    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private String id = ChatId.generateUUID(ChatId.USER);

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    // boolean 자료형으로 비교만 하도록 수정
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    // AI 포털 핵심: 플랜 및 토큰 관리
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private PlanEntity plan;

    // 잔여 토큰량
    @Column(name = "remainingTokens", nullable = false)
    private int remainingTokens;


    // 비밀번호 재설정 토큰 로직
    private String resetToken;
    private LocalDateTime tokenExpiry;


    // 비밀번호 변경 적용
    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    // 비밀번호 재설정 토큰 발급
    public void generateResetToken(String token) {
        this.resetToken = token;
    }

    // 비밀번호 변경 완료 후 토큰 폐기
    public void clearResetToken() {
        this.resetToken = null;
    }


    // 비즈니스 로직: 토큰 차감 메서드
    public void decreaseTokens(int usedTokens) {
        if (this.remainingTokens < usedTokens) {
            throw new IllegalArgumentException("잔여 토큰이 부족합니다.");
        }
        this.remainingTokens -= usedTokens;
    }


    // 정보 수정
    public void updateProfile(String username, String encodePassword) {
        if (username != null && !username.isBlank()) {
            this.username = username;
        }
        if (encodePassword != null && !encodePassword.isBlank()) {
            this.password = encodePassword;
        }
    }


    // 회원 탈퇴
    public void withdraw() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴 처리된 계정입니다.");
        }
        this.status = UserStatus.WITHDRAWN;
    }
}