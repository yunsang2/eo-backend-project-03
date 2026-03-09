package com.example.chat.security.jwt;

import com.example.chat.domain.user.jwt.JwtDto;
import com.example.chat.security.CustomUserDetails;
import io.jsonwebtoken.Jwts;
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
    private final JwtProperties jwtProperties;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        secretKey = new SecretKeySpec(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8),
                Jwts.SIG.HS256.key().build().getAlgorithm());
    }

    public String getUsername(String token) {
        return getClaim(token, "username");
    }

    public String getAuthority(String token) {
        return getClaim(token, "authority");
    }

    public String getClaim(String token, String claim) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(claim, String.class);
    }

    public boolean isExpired(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }

    public String issueAccessToken(String username, String authority) {
        return Jwts.builder()
                .claim("username", username)
                .claim("authority", authority)
                .issuedAt(new Date())
                .expiration(getDateAfterDuration(jwtProperties.accessTokenExpiration()))
                .signWith(secretKey)
                .compact();
    }

    public String issueRefreshToken(String username) {
        return Jwts.builder()
                .claim("username", username)
                .issuedAt(new Date())
                .expiration(getDateAfterDuration(jwtProperties.refreshTokenExpiration()))
                .signWith(secretKey)
                .compact();
    }

    private Date getDateAfterDuration(Duration duration) {
        return new Date(new Date().getTime() + duration.toMillis());
    }

    public JwtDto.Response getJwtResponseDto(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        assert userDetails != null;
        String email = userDetails.getUsername();
        String authority = userDetails.getAuthorities().iterator().next().getAuthority();

        String accessToken = issueAccessToken(email, authority);
        String refreshToken = issueRefreshToken(email);

        return new JwtDto.Response(
                "Bearer",
                accessToken,
                refreshToken
        );
    }
}