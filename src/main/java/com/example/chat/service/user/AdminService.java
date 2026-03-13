package com.example.chat.service.user;


import com.example.chat.domain.user.dto.AdminDto;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final MessageRepository messageRepository;

    /**
     * 전체 유저 목록 조회 (/admin/users)
     */
    public List<AdminDto.UserListItem> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> new AdminDto.UserListItem(
                        user.getId(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getRole().name(),
                        user.getStatus().name(),
                        user.getPlan() != null ? user.getPlan().getName() : "없음",
                        user.getRemainingTokens(),
                        user.getCreatedAt()
                ))
                .toList();
    }

    /**
     *  계정 상태 변경 (/admin/users/{userId}/status)
     */
    @Transactional
    public void updateUserStatus(String userId, UserStatus status) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        user.updateStatus(status);
    }

    /**
     *  플랜 설정 수정 (/admin/plans/{planId})
     */
    @Transactional
    public void updatePlanSettings(String planId, AdminDto.PlanUpdate request) {
        PlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new NoSuchElementException("플랜을 찾을 수 없습니다."));

        // 플랜 엔티티의 필드 수정
        plan.updateSettings(request.limitTokens(), request.availableModels());
    }

    @Transactional
    public List<AdminDto.PlanUsageResponse> getPlanUsageStats() {
        return userRepository.countUsersByPlan().stream()
                .map(p -> new AdminDto.PlanUsageResponse(p.getPlanName(), p.getUserCount()))
                .toList();
        }

    @Transactional
    public List<AdminDto.ModelUsageResponse> getModelUsageStats() {
        return messageRepository.sumTokensByModel().stream()
                .map(m -> new AdminDto.ModelUsageResponse(m.getModelName(), m.getTotalUsedTokens()))
                .toList();
    }
}
