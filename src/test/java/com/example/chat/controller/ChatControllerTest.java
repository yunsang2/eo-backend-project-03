package com.example.chat.controller;

import com.example.chat.domain.chat.ChatType;
import com.example.chat.domain.chat.ai.AiDto;
import com.example.chat.domain.chat.message.MessageDto;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.SessionRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.service.AiClient;
import com.example.chat.security.CustomUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret-key=7kQ43HmV4oMHcZgyNfsxbVoYDSdN9WBvJCn8KKM3k5v7kQ43HmV4oMHcZgyNfsxbVoYDSdN9WBvJCn8KKM3k5v",
        "jwt.access-token-expiration=1h",
        "jwt.refresh-token-expiration=7d",
        "alan.api.key=test-api-key"
})
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private AiClient aiClient;

    private UserEntity testUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        // 실패 시나리오(모델 제한 등)를 원활히 테스트하기 위해 BASIC 플랜으로 세팅
        PlanEntity basicPlan = planRepository.findByName("BASIC")
                .orElseGet(() -> planRepository.save(PlanEntity.builder()
                        .name("BASIC")
                        .price(0)
                        .limitTokens(5000)
                        .availableModels("gpt-3.5-turbo,gpt-4o-mini")
                        .build()));

        testUser = userRepository.findByEmail("test@gmail.com").orElseGet(() ->
                userRepository.save(UserEntity.builder()
                        .email("test@gmail.com")
                        .username("testuser_unique")
                        .password(passwordEncoder.encode("1234"))
                        .role(UserRole.USER)
                        .status(com.example.chat.domain.user.user_enum.UserStatus.ACTIVE)
                        .plan(basicPlan)
                        .remainingTokens(5000)
                        .build())
        );

        userDetails = mock(CustomUserDetails.class);
        given(userDetails.getId()).willReturn(testUser.getId());
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();

        // 외부 AI API
        given(aiClient.getAiAnswer(anyString(), anyString(), any(ChatType.class)))
                .willReturn(new AiDto.Response("통합 테스트 답변입니다.", 100));
    }

    @Test
    @DisplayName("성공: 일반 대화 시 로직(토큰 차감, 세션 생성)이 정상 작동한다")
    void askChat_Integration_Success() throws Exception {
        // BASIC 플랜이므로 제한에 걸리지 않는 "gpt-3.5-turbo"를 사용
        MessageDto.Request request = new MessageDto.Request("안녕하세요", "gpt-3.5-turbo");

        mockMvc.perform(post("/api/chat/ask")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()) // 200 OK
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("통합 테스트 답변입니다."));

        UserEntity user = userRepository.findByEmail("test@gmail.com").orElseThrow();
        assertThat(user.getRemainingTokens()).isEqualTo(4900);
    }

    @Test
    @DisplayName("실패: BASIC 플랜 사용자가 gpt-4 모델을 요청하면 400 에러가 발생한다")
    void askChat_Integration_Fail_PlanRestriction() throws Exception {
        // gpt-4 모델명 사용
        MessageDto.Request request = new MessageDto.Request("안녕하세요", "gpt-4");

        mockMvc.perform(post("/api/chat/ask")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // 400 Bad Request
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("현재 플랜에서 지원하지 않는 모델입니다."));
    }

    @Test
    @DisplayName("실패: 토큰이 부족하면 403 에러가 발생한다")
    void askChat_Integration_Fail_OutOfTokens() throws Exception {
        // Given: 유저의 토큰을 0으로 강제 소진시킴
        testUser.decreaseTokens(testUser.getRemainingTokens());
        userRepository.save(testUser);

        MessageDto.Request request = new MessageDto.Request("안녕하세요", "gpt-3.5-turbo");

        // When & Then
        mockMvc.perform(post("/api/chat/ask")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden()) // 🚨 403 Forbidden
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("토큰이 부족합니다. 플랜을 업그레이드하거나 충전해주세요."));
    }

    @Test
    @DisplayName("실패: BASIC 플랜 사용자가 SUMMARY 기능을 요청하면 400 에러가 발생한다")
    void askChat_Integration_Fail_SummaryNotAllowed() throws Exception {
        MessageDto.Request request = new MessageDto.Request("http://example.com 요약해줘", "gpt-3.5-turbo");

        // 실제 컨트롤러에 명시된 URL인 /api/chat/summary 로 요청
        mockMvc.perform(post("/api/chat/summary")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("웹 페이지 요약 및 번역 기능은 PRO 플랜 이상부터 이용 가능합니다."));
    }


    @Test
    @DisplayName("실패: PREMIUM 미만(BASIC) 플랜 사용자가 YOUTUBE 기능을 요청하면 400 에러가 발생한다")
    void askChat_Integration_Fail_YoutubeNotAllowed() throws Exception {
        MessageDto.Request request = new MessageDto.Request("https://youtube.com/watch?v=123 요약해줘", "gpt-3.5-turbo");

        mockMvc.perform(post("/api/chat/youtube")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("유튜브 영상 요약 기능은 PREMIUM 플랜 전용 서비스입니다."));
    }

    @Test
    @DisplayName("성공: PREMIUM 플랜 사용자가 YOUTUBE 기능을 요청하면 정상 작동한다")
    void askChat_Integration_Success_YoutubeAllowed() throws Exception {
        // Given: PREMIUM 플랜 생성 및 프리미엄 유저 생성
        PlanEntity premiumPlan = planRepository.findByName("PREMIUM")
                .orElseGet(() -> planRepository.save(PlanEntity.builder()
                        .name("PREMIUM")
                        .price(20000)
                        .limitTokens(100000)
                        .availableModels("gpt-3.5-turbo,gpt-4,gpt-4o-mini")
                        .build()));

        UserEntity premiumUser = userRepository.save(UserEntity.builder()
                .email("premium@gmail.com")
                .username("premium_user")
                .password(passwordEncoder.encode("1234"))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .plan(premiumPlan)
                .remainingTokens(100000)
                .build());

        // 프리미엄 유저용 인증 객체(Mock) 생성
        CustomUserDetails premiumUserDetails = mock(CustomUserDetails.class);
        given(premiumUserDetails.getId()).willReturn(premiumUser.getId());
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(premiumUserDetails).getAuthorities();

        MessageDto.Request request = new MessageDto.Request("https://youtube.com/watch?v=123 요약해줘", "gpt-3.5-turbo");

        // When & Then
        mockMvc.perform(post("/api/chat/youtube")
                        .with(csrf())
                        .with(user(premiumUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("통합 테스트 답변입니다."));
    }
}