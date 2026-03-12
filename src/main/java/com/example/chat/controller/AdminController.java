package com.example.chat.controller;

import com.example.chat.domain.chat.TokenResetScheduler;
import com.example.chat.domain.user.dto.AdminDto;
import com.example.chat.domain.ApiResponseDto;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.service.user.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final TokenResetScheduler tokenResetScheduler;

    /**
     *  전체 유저 관리
     *  GET /admin/users
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponseDto<List<AdminDto.UserListItem>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponseDto.success("유저 목록 조회 성공", adminService.getAllUsers()));
    }

    /**
     *  계정 상태 변경
     *  PATCH /admin/user/{userId}/status
     */
    @PatchMapping("/user/{userId}/status")
    public ResponseEntity<ApiResponseDto<Void>> changeUserStatus(
            @PathVariable String userId,
            @RequestParam UserStatus status) {

        adminService.updateUserStatus(userId, status);
        return ResponseEntity.ok(ApiResponseDto.success("사용자 상태가 " + status + "(으)로 변경되었습니다."));
    }

    /**
     *  플랜 설정 수정
     *  PUT /admin/plans/{planId}
     */
    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponseDto<Void>> updatePlan(
            @PathVariable String planId,
            @RequestBody AdminDto.PlanUpdate request) {

        adminService.updatePlanSettings(planId, request);
        return ResponseEntity.ok(ApiResponseDto.success("플랜 설정이 수정되었습니다."));
    }

    /**
     *  사용량 통계
     *  GET /admin/stats/usage
     */
    @GetMapping("/stats/usage")
    public ResponseEntity<ApiResponseDto<Void>> getUsageStats() {
        return ResponseEntity.ok(ApiResponseDto.success("사용량 통계 조회 (준비 중)"));
    }



    /**
     * 전 사용자 토큰 수동 초기화 API (테스트 및 관리용)
     * POST /api/admin/tokens/reset
     */
    @PostMapping("/tokens/reset")
    public ResponseEntity<ApiResponseDto<Void>> manualTokenReset() {
        // 스케줄러 내부의 로직을 그대로 호출합니다.
        tokenResetScheduler.resetDailyTokens();
        return ResponseEntity.ok(ApiResponseDto.success("모든 사용자의 토큰이 즉시 초기화되었습니다."));
    }
}
