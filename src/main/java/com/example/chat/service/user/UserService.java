package com.example.chat.service.user;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.EmailVerificationEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.jwt.JwtDto;
import com.example.chat.domain.user.user_enum.UserProvider;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.domain.user.dto.UserDto;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.EmailRepository;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Objects;
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
        EmailVerificationEntity verification = verificationRepository.findById(request.email())
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 먼저 진행해 주세요."));

        if (!verification.isVerified()) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        PlanEntity basicPlan = planRepository.findByName("BASIC")
                .orElseThrow(() -> new IllegalArgumentException("시스템 에러: 기본 플랜(BASIC)을 찾을 수 없습니다."));

        long userCount = userRepository.count();
        UserRole assignedRole = (userCount == 0) ? UserRole.ADMIN : UserRole.USER;

        UserEntity newUser = UserEntity.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .username(request.username())
                .role(assignedRole)
                .status(UserStatus.ACTIVE)
                .provider(UserProvider.LOCAL)
                .plan(basicPlan)
                .remainingTokens(basicPlan.getLimitTokens())
                .build();

        verificationRepository.delete(verification);
        UserEntity savedUser = userRepository.save(newUser);
        return UserDto.Response.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public JwtDto.Response login(UserDto.LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new IllegalArgumentException("관리자에 의해 정지된 계정입니다.");
        }
        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴 처리된 계정입니다.");
        }

        String authority = "ROLE_" + user.getRole().name();
        String accessToken = jwtProvider.issueAccessToken(user.getId(), user.getEmail(), authority);
        String refreshToken = jwtProvider.issueRefreshToken(user.getId(), user.getEmail());

        return new JwtDto.Response("Bearer", accessToken, refreshToken);
    }

    @Transactional
    public void withdrawUser(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        user.withdraw();
    }

    @Transactional
    public void forgotPassword(UserDto.PasswordForgotRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 소셜 연동 계정은 자체 비밀번호가 없으므로 찾기 불가
        if (user.getProvider() != UserProvider.LOCAL) {
            throw new IllegalArgumentException("소셜 로그인 연동 계정은 이 기능을 사용할 수 없습니다.");
        }

        String resetToken = UUID.randomUUID().toString();
        user.generateResetToken(resetToken);
        mailService.sendPasswordResetMail(user.getEmail(), resetToken);
    }

    @Transactional
    public void resetPassword(UserDto.PasswordResetRequest request) {
        UserEntity user = userRepository.findByResetToken(request.resetToken())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 재설정 토큰입니다."));

        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.updatePassword(encodedPassword);
        user.clearResetToken();
    }

    @Transactional(readOnly = true)
    public UserDto.Response getMyInfo(String userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));
        return UserDto.Response.fromEntity(user);
    }

    @Transactional
    public UserDto.Response updateMyInfo(String userId, UserDto.UpdateRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        // 소셜 연동 계정은 비밀번호 변경 불가
        boolean isSocialUser = user.getProvider() != UserProvider.LOCAL;
        boolean isTryingToChangePassword = request.newPassword() != null && !request.newPassword().isBlank();

        if (isSocialUser && isTryingToChangePassword) {
            throw new IllegalArgumentException("소셜 로그인 연동 계정은 비밀번호를 변경할 수 없습니다.");
        }

        String encodePassword = user.getPassword();

        // 비밀번호 변경 처리 (LOCAL 유저만 해당)
        if (isTryingToChangePassword) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new IllegalArgumentException("비밀번호 변경을 위해 현재 비밀번호를 입력해주세요.");
            }
            if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }
            encodePassword = passwordEncoder.encode(request.newPassword());
        }

        user.updateProfile(request.username(), encodePassword);
        return UserDto.Response.fromEntity(user);
    }
}