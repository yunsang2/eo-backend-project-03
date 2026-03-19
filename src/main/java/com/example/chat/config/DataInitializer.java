package com.example.chat.config;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.PlanRepository; // 본인의 PlanRepository 경로에 맞게 수정해주세요
import com.example.chat.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 애플리케이션 시작 시 DB에 기본 데이터가 없으면 자동으로 채워주는 클래스
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // DB에 플랜 정보가 하나도 없을 때만 기본 플랜 3가지를 생성
        if (planRepository.count() == 0) {
            List<PlanEntity> defaultPlans = List.of(
                    PlanEntity.builder()
                            .name("BASIC")
                            .price(0)
                            .limitTokens(5000)
                            .availableModels("gpt-3.5-turbo,gpt-4o-mini")
                            .build(),

                    PlanEntity.builder()
                            .name("PRO")
                            .price(9900)
                            .limitTokens(50000)
                            .availableModels("gpt-3.5-turbo,gpt-4o-mini")
                            .build(),

                    PlanEntity.builder()
                            .name("PREMIUM")
                            .price(19900)
                            .limitTokens(200000)
                            .availableModels("gpt-3.5-turbo,gpt-4,gpt-4o-mini")
                            .build()
            );

            planRepository.saveAll(defaultPlans);
        }

        // 2. 테스트용 계정 자동 생성 (이메일 인증 스킵 목적)
        if (userRepository.findByEmail("test@gmail.com").isEmpty()) {
            PlanEntity basicPlan = planRepository.findByName("BASIC")
                    .orElseThrow(() -> new RuntimeException("BASIC 플랜을 찾을 수 없습니다."));

            UserEntity testUser = UserEntity.builder()
                    .email("test@gmail.com")
                    // 테스트용 비밀번호 암호화 적용
                    .password(passwordEncoder.encode("Password123!"))
                    .username("tester")
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .plan(basicPlan)
                    .remainingTokens(5000)
                    .build();

            userRepository.save(testUser);
            System.out.println("✅ 테스트용 계정 자동 생성 완료 (ID: test@gmail.com / PW: Password123!)");
        }

//        userRepository.findByEmail("test@gmail.com").ifPresent(user -> {
//            PlanEntity premiumPlan = planRepository.findByName("PREMIUM").orElseThrow();
//            user.upgradePlan(premiumPlan);
//            userRepository.save(user);
//            System.out.println("🚀 테스트 유저(test@gmail.com)가 PREMIUM 플랜으로 강제 업그레이드 되었습니다!");
//        });
    }
}