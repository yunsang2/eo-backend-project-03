package com.example.chat.controller;

import com.example.chat.domain.user.dto.AdminDto;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.security.CustomUserDetails;
import com.example.chat.service.user.AdminService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    // 🚨 직접 주입 대신 setUp에서 초기화하여 NPE 및 시간 포맷 에러 방지
    private ObjectMapper objectMapper;
    private CustomUserDetails adminUser;
    private CustomUserDetails normalUser;

    @BeforeEach
    void setUp() {
        // ObjectMapper 수동 설정
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // 권한 테스트를 위한 사용자 객체 생성
        adminUser = new CustomUserDetails("admin-id", "admin@test.com", "1234", UserRole.ADMIN);
        normalUser = new CustomUserDetails("user-id", "user@test.com", "1234", UserRole.USER);
    }

    @Test
    @DisplayName("성공: ADMIN 권한으로 유저 목록 조회 시 200 OK를 반환한다")
    void getAllUsers_Success_WithAdmin() throws Exception {
        // Given
        AdminDto.UserListItem mockUser = new AdminDto.UserListItem(
                "user-1", "test@test.com", "tester", "USER", "ACTIVE", "BASIC", 5000, LocalDateTime.now()
        );
        given(adminService.getAllUsers()).willReturn(List.of(mockUser));

        // When & Then
        // 🚨 AdminController의 @RequestMapping("/api/admin")과 @GetMapping("/users")가 맞는지 확인
        mockMvc.perform(get("/api/admin/users")
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("test@test.com"));
    }

    @Test
    @DisplayName("실패: 일반 USER가 관리자 API 접근 시 403 Forbidden 에러가 발생한다")
    void getAllUsers_Fail_WithUser() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/users")
                        .with(user(normalUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("성공: 관리자가 특정 유저의 상태를 변경할 수 있다")
    void changeUserStatus_Success() throws Exception {
        // When & Then
        // 🚨 경로를 /api/admin/users/... 로 수정 (Plural naming 통일)
        mockMvc.perform(patch("/api/admin/users/user-1/status")
                        .param("status", "LOCKED")
                        .with(csrf())
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사용자 상태가 LOCKED(으)로 변경되었습니다."));
    }

    @Test
    @DisplayName("성공: 관리자 권한으로 플랜별 통계 조회 시 200 OK와 데이터를 반환한다")
    void getPlanStats_Success_WithAdmin() throws Exception {
        // given
        List<AdminDto.PlanUsageResponse> mockStats = List.of(
                new AdminDto.PlanUsageResponse("BASIC", 10L),
                new AdminDto.PlanUsageResponse("PRO", 5L)
        );
        given(adminService.getPlanUsageStats()).willReturn(mockStats);

        // when & then
        mockMvc.perform(get("/api/admin/stats/plans")
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].planName").value("BASIC"))
                .andExpect(jsonPath("$.data[0].userCount").value(10))
                .andExpect(jsonPath("$.data[1].planName").value("PRO"))
                .andExpect(jsonPath("$.data[1].userCount").value(5));
    }

    @Test
    @DisplayName("성공: 관리자 권한으로 AI 모델별 사용량 통계를 조회한다")
    void getModelStats_Success() throws Exception {
        // Given
        List<AdminDto.ModelUsageResponse> mockModelStats = List.of(
                new AdminDto.ModelUsageResponse("ALAN-K", 50000L),
                new AdminDto.ModelUsageResponse("ALAN-X", 30000L)
        );
        given(adminService.getModelUsageStats()).willReturn(mockModelStats);

        // When & Then
        mockMvc.perform(get("/api/admin/stats/usage")
                        .with(user(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].modelName").value("ALAN-K"))
                .andExpect(jsonPath("$.data[0].totalUsedTokens").value(50000))
                .andExpect(jsonPath("$.data[1].modelName").value("ALAN-X"))
                .andExpect(jsonPath("$.data[1].totalUsedTokens").value(30000));
    }

    @Test
    @DisplayName("실패: 일반 유저가 통계 API 접근 시 403 Forbidden을 반환한다")
    void getStats_Fail_WithUser() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/stats/plans")
                        .with(user(normalUser)))
                .andExpect(status().isForbidden());
    }
}