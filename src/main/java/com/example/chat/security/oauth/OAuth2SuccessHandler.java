package com.example.chat.security.oauth;

import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    // 로그인하기 위해 이동해야할 url
    // http://localhost:8080/oauth2/authorization/google
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        // DB에서 유저 확인 (CustomOAuth2UserService에서 이미 가입 처리됨)
        UserEntity user = userRepository.findByEmail(email).orElseThrow();

        // JWT 발급
        String authority = "ROLE_" + user.getRole().name();
        String accessToken = jwtProvider.issueAccessToken(user.getId(), user.getEmail(), authority);
        String refreshToken = jwtProvider.issueRefreshToken(user.getId(), user.getEmail());

        // 쿠키 설정 (기존 UserController의 로직과 동일)
        ResponseCookie accessCookie = createCookie("accessToken", accessToken, 60 * 60);
        ResponseCookie refreshCookie = createCookie("refreshToken", refreshToken, 7 * 24 * 60 * 60);

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // 로그인 후 이동할 프론트엔드 주소
        response.sendRedirect("http://localhost:8080/");
    }

    private ResponseCookie createCookie(String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}