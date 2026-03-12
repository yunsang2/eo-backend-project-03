package com.example.chat.controller;

import com.example.chat.domain.user.dto.AdminDto;
import com.example.chat.service.user.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret-key=7kQ43HmV4oMHcZgyNfsxbVoYDSdN9WBvJCn8KKM3k5v7kQ43HmV4oMHcZgyNfsxbVoYDSdN9WBvJCn8KKM3k5v",
        "jwt.access-token-expiration=1h",
        "jwt.refresh-token-expiration=7d",
        "alan.api.key=test-api-key"
})
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;


    @Test
    @WithMockUser(roles = "ADMIN") // 🚨 ADMIN 권한으로 접속 가정
    @DisplayName("성공: ADMIN 권한으로 유저 목록 조회 시 200 OK를 반환한다")
    void getAllUsers_Success_WithAdmin() throws Exception {
        // Given
        AdminDto.UserListItem mockUser = new AdminDto.UserListItem(
                "user-1", "test@test.com", "tester", "USER", "ACTIVE", "BASIC", 5000, LocalDateTime.now()
        );
        given(adminService.getAllUsers()).willReturn(List.of(mockUser));

        // When & Then
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("test@test.com"));
    }

    @Test
    @WithMockUser(roles = "USER") // 🚨 일반 USER 권한으로 접속 시도
    @DisplayName("실패: 일반 USER가 관리자 API 접근 시 403 Forbidden 에러가 발생한다")
    void getAllUsers_Fail_WithUser() throws Exception {
        // When & Then
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("성공: 관리자가 특정 유저의 상태를 변경할 수 있다")
    void changeUserStatus_Success() throws Exception {
        // When & Then (Patch 요청 테스트)
        mockMvc.perform(patch("/admin/user/user-1/status")
                        .param("status", "LOCKED")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("사용자 상태가 LOCKED(으)로 변경되었습니다."));
    }
}