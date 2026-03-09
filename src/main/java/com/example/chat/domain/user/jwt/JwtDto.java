package com.example.chat.domain.user.jwt;

public class JwtDto {
    public record Response(
            String grantType,
            // 짧은 수명의 출입증 (API 요청용)
            String accessToken,
            // 긴 수명의 재발급권 (자동 로그인용)
            String refreshToken
    ) {}
}
