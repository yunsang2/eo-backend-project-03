package com.example.chat.security.jwt;

import com.example.chat.domain.user.jwt.JwtDto;
import com.example.chat.security.CustomUserDetails;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;

    @Value("${jwt.access-token-expiration:1h}")
    private Duration accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:7d}")
    private Duration refreshTokenExpiration;

    // 생성자를 통해 secretKey를 주입받고 초기화
    public JwtProvider(@Value("${jwt.secret-key:7kQ43HmV4oMHcZgyNfsxbVoYDSdN9WBvJCn8KKM3k5v7kQ43HmV4oMHcZgyNfsxbVoYDSdN9WBvJCn8KKM3k5v}") String secretKeyString) {
        this.secretKey = new SecretKeySpec(secretKeyString.getBytes(StandardCharsets.UTF_8),
                Jwts.SIG.HS256.key().build().getAlgorithm());
    }

    // --- 정보 추출 (항상 userId가 있다고 가정) ---
    public String getUsername(String token) { return getClaim(token, "username"); }
    public String getAuthority(String token) { return getClaim(token, "authority"); }
    public String getUserId(String token) { return getClaim(token, "userId"); }

    public String getClaim(String token, String claim) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(claim, String.class);
    }

    // --- 만료 확인 ---
    public boolean isExpired(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getExpiration()
                    .before(new Date());
        } catch (Exception e) {
            // 라이브러리 특성상 토큰이 만료되면 에러가 터지므로, 여기서 캐치하여 true를 반환
            return true;
        }
    }

    // --- 토큰 발행 (단일화: 무조건 userId를 받도록 함) ---

    /**
     * Access Token 발행
     * UserService.login() 및 JwtProvider.getJwtResponseDto()에서 사용
     */
    public String issueAccessToken(String userId, String username, String authority) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("authority", authority)
                .issuedAt(new Date())
                .expiration(getDateAfterDuration(accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Refresh Token 발행
     * 리프레시 토큰에도 userId를 넣어 보안과 편의성을 확보
     */
    public String issueRefreshToken(String userId, String username) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(getDateAfterDuration(refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    // 파라미터가 null일 경우 무조건 기본값(1시간=3600000ms)을 반환하여 NPE를 철저히 방어
    private Date getDateAfterDuration(Duration duration) {
        long millis = (duration != null) ? duration.toMillis() : 3600000L;
        return new Date(System.currentTimeMillis() + millis);
    }

    /**
     * 로그인 성공 시 호출되어 응답 DTO를 만드는 핵심 메서드
     */
    public JwtDto.Response getJwtResponseDto(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        assert userDetails != null;

        // CustomUserDetails가 들고 있는 UUID(id)를 추출
        String userId = userDetails.getId();
        String email = userDetails.getUsername();
        String authority = userDetails.getAuthorities().iterator().next().getAuthority();

        // 통합된 단일 메서드 호출
        String accessToken = issueAccessToken(userId, email, authority);
        String refreshToken = issueRefreshToken(userId, email);

        return new JwtDto.Response(
                "Bearer",
                accessToken,
                refreshToken
        );
    }
}