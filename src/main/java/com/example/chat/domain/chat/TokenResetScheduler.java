package com.example.chat.domain.chat;

import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenResetScheduler {

    private final UserRepository userRepository;

    /**
     * 매일 자정(00:00:00)에 실행되어 모든 사용자의 토큰을 플랜 한도량으로 리셋
     * Cron 표현식: "초 분 시 일 월 요일"
     */
    @Transactional
    @Scheduled(cron = "0 0 0 * * *")
    public void resetDailyTokens() {
        log.info("[스케줄러] 매일 자정 토큰 초기화 작업을 시작합니다.");

        // 모든 사용자 목록 조회 (작음 규모이기에 findAll 사용)
        List<UserEntity> allUsers = userRepository.findAll();

        int successCount = 0;
        for (UserEntity user : allUsers) {
            try {
                // 각 유저의 엔티티 내부 리셋 로직 호출
                user.resetTokens();
                successCount++;
            } catch (Exception e) {
                log.error("[스케줄러] 유저 ID: {} 토큰 초기화 중 오류 발생: {}", user.getId(), e.getMessage());
            }
        }

        log.info("[스케줄러] 토큰 초기화 완료. 대상: {}명 / 성공: {}명", allUsers.size(), successCount);
    }
}