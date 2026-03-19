package com.example.chat.security.oauth;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 구글 API를 통해 사용자 정보를 가져옴 (실제 런타임 시 실행)
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 가져온 정보를 바탕으로 DB 저장/조회 로직 실행
        return registerOrUpdateUser(oAuth2User);
    }

    @Transactional
    public OAuth2User registerOrUpdateUser(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        // 이메일로 기존 유저가 있는지 확인하고, 없으면 새로 가입시킴
        userRepository.findByEmail(email)
                .orElseGet(() -> {
                    // 신규 가입 시 기본 BASIC 플랜 할당
                    PlanEntity basicPlan = planRepository.findByName("BASIC")
                            .orElseThrow(() -> new RuntimeException("시스템 에러: 기본 플랜(BASIC)이 존재하지 않습니다."));

                    return userRepository.save(UserEntity.builder()
                            .email(email)
                            // 중복 방지를 위한 랜덤 닉네임 생성
                            .username(name + "_" + UUID.randomUUID().toString().substring(0, 5))
                            // 소셜 로그인 유저는 비밀번호를 사용하지 않으므로 랜덤값 저장
                            .password(UUID.randomUUID().toString())
                            .role(UserRole.USER)
                            .status(UserStatus.ACTIVE)
                            .plan(basicPlan)
                            .remainingTokens(basicPlan.getLimitTokens())
                            .build());
                });

        return oAuth2User;
    }
}