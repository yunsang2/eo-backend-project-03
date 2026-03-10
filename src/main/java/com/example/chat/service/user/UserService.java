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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailRepository verificationRepository;
    private final MailService mailService;

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
                // 가입시 기본 플랜 및 토큰제공
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

        String accessToken = jwtProvider.issueAccessToken(user.getId(), user.getEmail(), authority);
        String refreshToken = jwtProvider.issueRefreshToken(user.getId(), user.getEmail());

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


    // 비밀번호 찾기 요청 (토큰 생성 및 메일 발송)
    @Transactional
    public void forgotPassword(UserDto.PasswordForgotRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 유추하기 힘든 랜덤 UUID 토큰 생성
        String resetToken = UUID.randomUUID().toString();
        user.generateResetToken(resetToken);

        // 메일 발송
        mailService.sendPasswordResetMail(user.getEmail(), resetToken);
    }

    // 비밀번호 재설정 실행
    @Transactional
    public void resetPassword(UserDto.PasswordResetRequest request) {
        // 토큰으로 유저 찾기
        UserEntity user = userRepository.findByResetToken(request.resetToken())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 재설정 토큰입니다."));

        // 새 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.updatePassword(encodedPassword);
        user.clearResetToken();
    }


    // 정보 조회
    @Transactional
    public UserDto.Response getMyInfo(String userId) {
        // 유저 조회
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 계정입니다."));

        // Entity -> Dto 변환
        return UserDto.Response.fromEntity(user);
    }


    // 정보 수정
    @Transactional
    public UserDto.Response updateMyInfo(String userId, UserDto.UpdateRequest request) {
        // 유저 조회
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 계정입니다."));

        // 유저네임 중복 체크
        if (!user.getUsername().equals(request.username())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new IllegalArgumentException("이미 사용 중인 유저네임입니다.");
            }
        }

        // 비밀번호 변경 로직
        String encodePassword = user.getPassword();

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            // 현재 비밀번호가 입력되었고 실제 DB 값과 일치하는지 검증
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new IllegalArgumentException("비밀번호 변경을 위해 현재 비밀번호를 입력해주세요.");
            }

            if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }

            // 검증 통과 시 새로운 비밀번호 암호화
            encodePassword = passwordEncoder.encode(request.newPassword());
        }

        // 비밀번호 업데이트
        user.updateProfile(request.username(), encodePassword);

        return UserDto.Response.fromEntity(user);
    }

}