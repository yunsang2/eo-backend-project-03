package com.example.chat.domain.user.dto;

import java.time.LocalDateTime;

public class AdminDto {

    // 전체 유저 목록
    public record UserListItem(
            String id,
            String email,
            String username,
            String role,
            String status,
            String planName,
            int remainingTokens,
            LocalDateTime createdAt
    ) {}

    // 사용량 통계
    public record UsageStats(
            String date,
            long totalTokenUsed,
            long messageCount
    ) {}

    // 매출 통계
    public record RevenueStats(
            long totalRevenue,
            long paymentCount
    ) {}

    // 플랜 설정 수정 요청
    public record PlanUpdate(
            int limitTokens,
            String availableModels
    ) {}

    // 플랜별 통계
    public record PlanUsageResponse(
            String planName,
            long userCount
    ) {}

    // 모델별 사용량
    public record ModelUsageResponse(
            String modelName,
            long totalUsedTokens
    ) {}
}