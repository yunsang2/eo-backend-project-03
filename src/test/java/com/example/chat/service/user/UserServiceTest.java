package com.example.chat.service.user;

import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.EmailVerificationEntity;
import com.example.chat.domain.user.UserDto;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.domain.user.user_enum.UserRole;
import com.example.chat.domain.user.user_enum.UserStatus;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.EmailRepository;
import com.example.chat.repository.user.UserRepository;
import com.example.chat.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private EmailRepository verificationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;


    // 회원가입(Signup) 테스트
    @Test
    @DisplayName("회원가입 성공 - 첫 가입자는 ADMIN 권한을 받는다")
    void signup_success_firstUserIsAdmin() {
        // given
        UserDto.SignUpRequest request = new UserDto.SignUpRequest("test@test.com", "Password123!", "tester", "홍길동");
        EmailVerificationEntity verifiedEmail = EmailVerificationEntity
                        .builder()
                        .email("test@test.com")
                        .isVerified(true)
                        .build();

        PlanEntity basicPlan = PlanEntity.builder().id("plan-1").name("BASIC").limitTokens(100).build();

        when(verificationRepository.findById(request.email())).thenReturn(Optional.of(verifiedEmail));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(planRepository.findByName("BASIC")).thenReturn(Optional.of(basicPlan));

        // 유저가 0명이라고 가짜 응답 세팅
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");

        UserEntity savedUser = UserEntity.builder()
                .id("user-1")
                .email(request.email())
                .username(request.username())
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .plan(basicPlan)
                .build();

        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        // when
        UserDto.Response response = userService.registerUser(request);

        // then
        // ADMIN 권한이 맞는지 확인
        assertThat(response.role()).isEqualTo("ADMIN");
        // 인증 데이터 삭제가 호출되었는지 확인
        verify(verificationRepository).delete(verifiedEmail);
    }


    @Test
    @DisplayName("회원가입 성공 - 두 번째 가입자부터는 USER 권한을 받는다")
    void signup_success_secondUserIsUser() {
        // given
        UserDto.SignUpRequest request = new UserDto.SignUpRequest("user2@test.com", "Password123!", "tester2", "이몽룡");
        EmailVerificationEntity verifiedEmail = EmailVerificationEntity.builder().email("user2@test.com").isVerified(true).build();
        PlanEntity basicPlan = PlanEntity.builder().id("plan-1").name("BASIC").limitTokens(100).build();

        when(verificationRepository.findById(request.email())).thenReturn(Optional.of(verifiedEmail));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(planRepository.findByName("BASIC")).thenReturn(Optional.of(basicPlan));

        // 이미 DB에 1명의 유저(어드민)가 가입되어 있다고 가짜 응답 세팅
        when(userRepository.count()).thenReturn(1L);
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");

        UserEntity savedUser = UserEntity.builder()
                .id("user-2")
                .email(request.email())
                .username(request.username())
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .plan(basicPlan)
                .build();

        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);

        // when
        UserDto.Response response = userService.registerUser(request);

        // then
        assertThat(response.role()).isEqualTo("USER");
        verify(verificationRepository).delete(verifiedEmail);
    }


    @Test
    @DisplayName("회원가입 실패 - 이메일 인증을 진행하지 않은 경우")
    void signup_fail_notVerified() {
        // given
        UserDto.SignUpRequest request = new UserDto.SignUpRequest("test@test.com", "Password123!", "tester", "홍길동");
        // 인증 데이터 없음
        when(verificationRepository.findById(request.email())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이메일 인증을 먼저 진행해 주세요");
    }


    @Test
    @DisplayName("회원가입 실패 - 이미 가입된 이메일인 경우")
    void signup_fail_duplicateEmail() {
        // given
        UserDto.SignUpRequest request = new UserDto.SignUpRequest("test@test.com", "Password123!", "tester", "홍길동");
        EmailVerificationEntity verifiedEmail = EmailVerificationEntity.builder().email("test@test.com").isVerified(true).build();

        when(verificationRepository.findById(request.email())).thenReturn(Optional.of(verifiedEmail));
        // 중복 이메일 발생
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 가입된 이메일입니다");
    }



    // 로그인(Login) 테스트
    @Test
    @DisplayName("로그인 성공 - 토큰 발급 및 유저 상태 확인")
    void login_success() {
        // given
        UserDto.LoginRequest request = new UserDto.LoginRequest("test@test.com", "Password123!");
        UserEntity user = UserEntity.builder()
                .id("user-1")
                .email("test@test.com")
                .password("encodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        // 비밀번호 일치
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);

        when(jwtProvider.issueAccessToken(anyString(), anyString())).thenReturn("mock-access-token");
        when(jwtProvider.issueRefreshToken(anyString())).thenReturn("mock-refresh-token");

        // when
        var response = userService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("mock-access-token");
        assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");
    }


    @Test
    @DisplayName("로그인 실패 - 존재하지 않는 이메일")
    void login_fail_emailNotFound() {
        // given
        UserDto.LoginRequest request = new UserDto.LoginRequest("ghost@test.com", "Password123!");
        // 유저 없음
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가입되지 않은 이메일입니다");
    }


    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_fail_wrongPassword() {
        // given
        UserDto.LoginRequest request = new UserDto.LoginRequest("test@test.com", "WrongPassword!");
        UserEntity user = UserEntity.builder().email("test@test.com").password("encodedPassword").build();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        // 비밀번호 틀림
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");
    }
}