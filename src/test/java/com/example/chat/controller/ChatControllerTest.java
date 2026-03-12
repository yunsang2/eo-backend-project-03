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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
        "alan.client-id=test-client-id",
        "alan.base-url=https://kdt-api-function.azurewebsites.net/api/v1"
})
@AutoConfigureMockMvc
@Transactional
class ChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private ObjectMapper objectMapper;

    @MockitoBean
    private AiClient aiClient;

    private UserEntity testUser;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        // 만약 objectMapper가 주입되지 않았을 경우를 대비한 수동 초기화
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // 1. BASIC 플랜 세팅
        PlanEntity basicPlan = planRepository.findByName("BASIC")
                .orElseGet(() -> planRepository.save(PlanEntity.builder()
                        .name("BASIC")
                        .price(0)
                        .limitTokens(5000)
                        .availableModels("gpt-3.5-turbo,gpt-4o-mini")
                        .build()));

        // 2. 테스트 유저 생성 (NPE 방지를 위해 role, status 명시)
        testUser = userRepository.findByEmail("test@gmail.com").orElseGet(() ->
                userRepository.save(UserEntity.builder()
                        .email("test@gmail.com")
                        .username("testuser_unique")
                        .password(passwordEncoder.encode("1234"))
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .plan(basicPlan)
                        .remainingTokens(5000)
                        .build())
        );

        // 3. Security 인증 객체 Mocking
        userDetails = mock(CustomUserDetails.class);
        given(userDetails.getId()).willReturn(testUser.getId());
        given(userDetails.getUsername()).willReturn(testUser.getEmail());
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();

        // 4. 외부 AI API 응답 Mocking
        given(aiClient.getAiAnswer(anyString(), anyString(), any(ChatType.class)))
                .willReturn(new AiDto.Response("통합 테스트 답변입니다.", 100));
    }

    @Test
    @DisplayName("성공: 일반 대화 시 로직(토큰 차감)이 정상 작동한다")
    void askChat_Integration_Success() throws Exception {
        MessageDto.Request request = new MessageDto.Request("안녕하세요", "gpt-3.5-turbo");

        mockMvc.perform(post("/api/chat/ask")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("통합 테스트 답변입니다."));

        UserEntity user = userRepository.findByEmail("test@gmail.com").orElseThrow();
        assertThat(user.getRemainingTokens()).isLessThan(5000);
    }

    @Test
    @DisplayName("실패: BASIC 플랜 사용자가 gpt-4 모델을 요청하면 400 에러가 발생한다")
    void askChat_Integration_Fail_PlanRestriction() throws Exception {
        MessageDto.Request request = new MessageDto.Request("안녕하세요", "gpt-4");

        mockMvc.perform(post("/api/chat/ask")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("현재 플랜에서 지원하지 않는 모델입니다."));
    }

    @Test
    @DisplayName("실패: 토큰이 부족하면 403 에러가 발생한다")
    void askChat_Integration_Fail_OutOfTokens() throws Exception {
        UserEntity user = userRepository.findById(testUser.getId()).orElseThrow();
        user.decreaseTokens(user.getRemainingTokens());
        userRepository.saveAndFlush(user);

        MessageDto.Request request = new MessageDto.Request("안녕하세요", "gpt-3.5-turbo");

        mockMvc.perform(post("/api/chat/ask")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("토큰이 부족합니다. 플랜을 업그레이드하거나 충전해주세요."));
    }

    @Test
    @DisplayName("실패: BASIC 플랜 사용자가 SUMMARY 기능을 요청하면 400 에러가 발생한다")
    void askChat_Integration_Fail_SummaryNotAllowed() throws Exception {
        MessageDto.Request request = new MessageDto.Request("http://example.com 요약해줘", "gpt-3.5-turbo");

        mockMvc.perform(post("/api/chat/summary")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("웹 페이지 요약 및 번역 기능은 PRO 플랜 이상부터 이용 가능합니다."));
    }

    @Test
    @DisplayName("성공: PREMIUM 플랜 사용자가 YOUTUBE 기능을 요청하면 정상 작동한다")
    void askChat_Integration_Success_YoutubeAllowed() throws Exception {
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

        CustomUserDetails premiumUserDetails = mock(CustomUserDetails.class);
        given(premiumUserDetails.getId()).willReturn(premiumUser.getId());
        given(premiumUserDetails.getUsername()).willReturn(premiumUser.getEmail());
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(premiumUserDetails).getAuthorities();

        MessageDto.Request request = new MessageDto.Request("https://youtube.com/watch?v=123", "gpt-3.5-turbo");

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