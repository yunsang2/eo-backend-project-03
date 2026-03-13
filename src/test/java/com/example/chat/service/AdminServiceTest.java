package com.example.chat.service;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.dto.AdminDto;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.service.user.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @InjectMocks
    private AdminService adminService;

    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MessageRepository messageRepository;

    // 전체 유저 목록 조회
    @Test
    @DisplayName("전체 유저 목록 조회 성공 - 모든 필드가 DTO로 변환되어야 한다")
    void getAllUsers_success() {
        // given
        PlanEntity plan = PlanEntity.builder().name("BASIC").build();
        UserEntity user = UserEntity.builder()
                .id("user-1")
                .email("admin@test.com")
                .username("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .plan(plan)
                .remainingTokens(5000)
                .build();

        when(userRepository.findAll()).thenReturn(List.of(user));

        // when
        List<AdminDto.UserListItem> result = adminService.getAllUsers();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("tester");
        assertThat(result.get(0).status()).isEqualTo("ACTIVE");
        assertThat(result.get(0).planName()).isEqualTo("BASIC");
    }

    // 유저 상태 변경
    @Test
    @DisplayName("유저 상태 변경 성공 - LOCKED로 상태 변경 시 Dirty Checking 발생")
    void updateUserStatus_success() {
        // given
        String userId = "user-1";
        UserEntity user = spy(UserEntity.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .build());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        adminService.updateUserStatus(userId, UserStatus.LOCKED);

        // then
        assertThat(user.getStatus()).isEqualTo(UserStatus.LOCKED);
        verify(user).updateStatus(UserStatus.LOCKED);
    }

    // 유저 상태 변경 실패
    @Test
    @DisplayName("유저 상태 변경 실패 - 존재하지 않는 유저 ID")
    void updateUserStatus_fail_notFound() {
        // given
        String userId = "none";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.updateUserStatus(userId, UserStatus.LOCKED))
                .isInstanceOf(NoSuchElementException.class);
    }

    // 플랜 설정 수정
    @Test
    @DisplayName("플랜 설정 수정 성공 - 토큰 한도와 허용 모델 정보 변경")
    void updatePlanSettings_success() {
        // given
        String planId = "plan-1";
        AdminDto.PlanUpdate request = new AdminDto.PlanUpdate(50000, "Alan-GPT, Alan-Search");
        PlanEntity plan = spy(PlanEntity.builder()
                .id(planId)
                .limitTokens(10000)
                .availableModels("Alan-GPT")
                .build());

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        // when
        adminService.updatePlanSettings(planId, request);

        // then
        assertThat(plan.getLimitTokens()).isEqualTo(50000);
        assertThat(plan.getAvailableModels()).contains("Alan-Search");
        verify(plan).updateSettings(50000, "Alan-GPT, Alan-Search");
    }

    @Test
    @DisplayName("성공: 플랜별 사용자 수 통계를 조회하여 DTO 리스트로 반환한다")
    void getPlanUsageStats_Success() {
        // given
        // 프로젝션 인터페이스를 Mockito로 모킹
        UserRepository.PlanCountProjection mockPlan1 = mock(UserRepository.PlanCountProjection.class);
        when(mockPlan1.getPlanName()).thenReturn("BASIC");
        when(mockPlan1.getUserCount()).thenReturn(10L);

        UserRepository.PlanCountProjection mockPlan2 = mock(UserRepository.PlanCountProjection.class);
        when(mockPlan2.getPlanName()).thenReturn("PRO");
        when(mockPlan2.getUserCount()).thenReturn(5L);

        when(userRepository.countUsersByPlan()).thenReturn(List.of(mockPlan1, mockPlan2));

        // when
        List<AdminDto.PlanUsageResponse> result = adminService.getPlanUsageStats();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).planName()).isEqualTo("BASIC");
        assertThat(result.get(0).userCount()).isEqualTo(10L);
        assertThat(result.get(1).planName()).isEqualTo("PRO");
        assertThat(result.get(1).userCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("성공: 모델별 AI 사용량 통계를 조회한다")
    void getModelUsageStats_Success() {
        // given
        MessageRepository.ModelUsageProjection mockUsage = mock(MessageRepository.ModelUsageProjection.class);
        when(mockUsage.getModelName()).thenReturn("ALAN-SEARCH");
        when(mockUsage.getTotalUsedTokens()).thenReturn(100000L);

        when(messageRepository.sumTokensByModel()).thenReturn(List.of(mockUsage));

        // when
        List<AdminDto.ModelUsageResponse> result = adminService.getModelUsageStats();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).modelName()).isEqualTo("ALAN-SEARCH");
        assertThat(result.get(0).totalUsedTokens()).isEqualTo(100000L);
    }
}
