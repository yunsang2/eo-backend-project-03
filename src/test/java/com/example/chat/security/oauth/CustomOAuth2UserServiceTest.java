package com.example.chat.security.oauth;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    @DisplayName("성공: 이미 가입된 구글 유저는 추가 회원가입 없이 정보를 반환한다")
    void registerOrUpdateUser_ExistingUser_Success() {
        // Given
        String email = "existing@gmail.com";
        OAuth2User mockOAuth2User = createMockOAuth2User(email, "기존유저");

        UserEntity existingUser = UserEntity.builder()
                .email(email)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        given(userRepository.findByEmail(email)).willReturn(Optional.of(existingUser));

        // When
        OAuth2User result = customOAuth2UserService.registerOrUpdateUser(mockOAuth2User);

        // Then
        assertThat(result.getAttribute("email").toString()).isEqualTo(email);
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("성공: 신규 구글 유저는 자동으로 회원가입(DB 저장) 처리된다")
    void registerOrUpdateUser_NewUser_AutoSignup_Success() {
        // Given
        String email = "newbie@gmail.com";
        String name = "신규유저";
        OAuth2User mockOAuth2User = createMockOAuth2User(email, name);

        given(userRepository.findByEmail(email)).willReturn(Optional.empty());

        PlanEntity basicPlan = PlanEntity.builder()
                .id("plan-1")
                .name("BASIC")
                .limitTokens(5000)
                .build();
        given(planRepository.findByName("BASIC")).willReturn(Optional.of(basicPlan));

        // When
        customOAuth2UserService.registerOrUpdateUser(mockOAuth2User);

        // Then
        verify(userRepository, times(1)).save(argThat(user -> {
            boolean isEmailMatch = user.getEmail().equals(email);
            boolean isRoleMatch = user.getRole() == UserRole.USER;
            boolean isPlanMatch = user.getPlan().getName().equals("BASIC");
            boolean isTokenMatch = user.getRemainingTokens() == 5000;
            return isEmailMatch && isRoleMatch && isPlanMatch && isTokenMatch;
        }));
    }

    /**
     * 가짜 구글 유저 객체 생성용 헬퍼
     */
    private OAuth2User createMockOAuth2User(String email, String name) {
        return new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("email", email, "name", name),
                "email"
        );
    }
}