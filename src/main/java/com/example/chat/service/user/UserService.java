package com.example.chat.service.user;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.EmailVerificationEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.jwt.JwtDto;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.domain.user.UserDto;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.EmailRepository;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailRepository verificationRepository;

    private final JwtProvider jwtProvider;

    @Transactional
    public UserDto.Response registerUser(UserDto.SignUpRequest request) {

        // 이메일 인증을 통과한 유저인지 확인
        EmailVerificationEntity verification = verificationRepository.findById(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 먼저 진행해 주세요."));

        if (!verification.isVerified()) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        // 중복 검사
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("이미 사용 중인 유저네임(아이디)입니다.");
        }

        // 기본 플랜 조회(기본 플랜은 만들어야됨)
        PlanEntity basicPlan = planRepository.findByName("BASIC")
                .orElseThrow(() -> new IllegalArgumentException("시스템 에러: 기본 플랜(BASIC)을 찾을 수 없습니다."));

        // 현재 가입된 유저가 0명이면 ADMIN, 아니면 USER 권한 부여
        long userCount = userRepository.count();
        UserRole assignedRole = (userCount == 0) ? UserRole.ADMIN : UserRole.USER;

        // 유저 객체 조립
        UserEntity newUser = UserEntity.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .username(request.username())
                .role(assignedRole)
                .status(UserStatus.ACTIVE)
                .plan(basicPlan)
                .remainingTokens(basicPlan.getLimitTokens())
                .build();

        // 임시 인증 데이터는 삭제
        verificationRepository.delete(verification);

        // DB 저장 및 반환
        UserEntity savedUser = userRepository.save(newUser);
        return UserDto.Response.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public JwtDto.Response login(UserDto.LoginRequest request) {

        // 이메일로 유저 찾기 (가입 안된 이메일이면 예외 발생)
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 계정 상태 확인 (정지되거나 탈퇴한 회원은 로그인 차단)
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalArgumentException("관리자에 의해 정지된 계정입니다.");
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴 처리된 계정입니다.");
        }

        // 권한 문자열 조립 (예: "ROLE_USER")
        String authority = "ROLE_" + user.getRole().name();

        String accessToken = jwtProvider.issueAccessToken(user.getEmail(), authority);
        String refreshToken = jwtProvider.issueRefreshToken(user.getEmail());

        // 포장해서 반환
        return new JwtDto.Response("Bearer", accessToken, refreshToken);
    }

    @Transactional
    public void withdrawUser(String userId) {
        // 유저 조회
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 계정입니다."));

        // 상태 변경
        user.withdraw();
    }
}