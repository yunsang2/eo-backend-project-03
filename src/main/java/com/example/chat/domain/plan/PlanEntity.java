package com.example.chat.domain.plan;

import com.example.chat.domain.BaseTimeEntity;
import com.example.chat.domain.ChatId;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "plans")
public class PlanEntity extends BaseTimeEntity {


    @Id
    @Builder.Default
    @Column(name = "id", nullable = false, unique = true, updatable = false)
    private String id = ChatId.generateUUID(ChatId.PLAN);

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    // 월간 제공 토큰량
    @Column(name = "limitTokens", nullable = false)
    private int limitTokens;

    // 접근 가능 모델
    @Column(name = "availableModels", nullable = false)
    private String availableModels;

    @Column(name = "price", nullable = false)
    private int price;



    // 관리자용 플랜 설정 수정
    public void updateSettings(int limitTokens, String availableModels) {
        if (limitTokens < 0) {
            throw new IllegalArgumentException("토큰 한도는 0보다 작을 수 없습니다.");
        }

        this.limitTokens = limitTokens;

        if (availableModels != null && !availableModels.isBlank()) {
            this.availableModels = availableModels;
        }
    }

    // 가격 수정
    public void updatePrice(int price) {
        this.price = price;
    }
}