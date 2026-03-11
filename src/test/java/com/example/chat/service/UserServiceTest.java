package com.example.chat.service;

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
import com.example.chat.service.user.UserService;
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

    /**
     * 테스트 돌린 부분
     * 회원가입 api
     * admin 임명 api
     * 회원가입 실패 (email 인증 X
     * 회원가입 실패 (이미 가입된 계정일경우
     * 로그인 api
     * 로그인 실패 (이메일이 없을 경우
     * 로그인 실패 (비밀번호가 안 맞는경우
     * 내정보 불러오기 api
     * 내정보 수정 api
     */


    /**
     * 테스트 돌려야할 부분
     *
     *
     *
     */


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

        when(jwtProvider.issueAccessToken(anyString(), anyString(), anyString())).thenReturn("mock-access-token");
        when(jwtProvider.issueRefreshToken(anyString(), anyString())).thenReturn("mock-refresh-token");

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

    @Test
    @DisplayName("내 정보 조회 성공 - 존재하는 유저 ID로 조회 시 응답 반환")
    void getMyInfo_success() {
        // given
        String userId = "user-1";
        PlanEntity basicPlan = PlanEntity.builder().id("plan-1").name("BASIC").limitTokens(100).build();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .email("test@test.com")
                .username("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .plan(basicPlan)
                .remainingTokens(100)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        UserDto.Response response = userService.getMyInfo(userId);

        // then
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("tester");
        assertThat(response.planName()).isEqualTo("BASIC");
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("내 정보 조회 실패 - 존재하지 않는 유저 ID")
    void getMyInfo_fail_notFound() {
        // given
        String userId = "non-existent-id";
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(userId))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }


    // 내 정보 수정 테스트
    @Test
    @DisplayName("내 정보 수정 성공 - 유저네임과 비밀번호 모두 수정")
    void updateMyInfo_success() {
        // given
        String userId = "user-1";
        UserDto.UpdateRequest request = new UserDto.UpdateRequest("newTester", "oldPassword123!", "newPassword123!");

        PlanEntity basicPlan = PlanEntity.builder().id("plan-1").name("BASIC").build();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .username("oldTester")
                .password("oldEncodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .plan(basicPlan)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(request.username())).thenReturn(false);


        // 현재 비밀번호 일치 확인
        when(passwordEncoder.matches(request.currentPassword(), user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode(request.newPassword())).thenReturn("newEncodedPassword");

        // when
        UserDto.Response response = userService.updateMyInfo(userId, request);

        // then
        assertThat(response.username()).isEqualTo("newTester");
        verify(passwordEncoder).encode("newPassword123!");
    }

    @Test
    @DisplayName("내 정보 수정 실패 - 이미 존재하는 유저네임으로 변경 시도")
    void updateMyInfo_fail_duplicateUsername() {
        // given
        String userId = "user-1";
        UserDto.UpdateRequest request = new UserDto.UpdateRequest("existingUser", "currentPass!", "newPass!");
        UserEntity user = UserEntity.builder()
                .id(userId)
                .username("myOldName")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateMyInfo(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 유저네임");
    }

    @Test
    @DisplayName("내 정보 수정 성공 - 비밀번호는 입력하지 않고 유저네임만 변경")
    void updateMyInfo_success_onlyUsername() {
        // given
        String userId = "user-1";
        UserDto.UpdateRequest request = new UserDto.UpdateRequest("onlyNameChange", "", "");

        PlanEntity basicPlan = PlanEntity.builder().id("plan-1").name("BASIC").build();
        UserEntity user = UserEntity.builder()
                .id(userId)
                .username("oldName")
                .password("oldEncodedPassword")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .plan(basicPlan)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(request.username())).thenReturn(false);

        // when
        UserDto.Response response = userService.updateMyInfo(userId, request);

        // then
        assertThat(response.username()).isEqualTo("onlyNameChange");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("내 정보 수정 성공 - 현재 비밀번호가 일치하면 새로운 비밀번호로 변경 가능하다")
    void updateMyInfo_success_passwordVerification() {
        // given
        String userId = "user-1";
        // 파라미터 순서 예시: username, currentPassword, newPassword
        UserDto.UpdateRequest request = new UserDto.UpdateRequest("newTester", "current123!", "new123!");

        UserEntity user = UserEntity.builder()
                .id(userId)
                .username("oldTester")
                .password("encodedOldPassword")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(request.username())).thenReturn(false);

        // 입력한 'current123!'가 DB의 'encodedOldPassword'와 맞다고 가정
        when(passwordEncoder.matches("current123!", "encodedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode("new123!")).thenReturn("encodedNewPassword");

        // when
        UserDto.Response response = userService.updateMyInfo(userId, request);

        // then
        assertThat(response.username()).isEqualTo("newTester");
        verify(passwordEncoder).matches("current123!", "encodedOldPassword");
        verify(passwordEncoder).encode("new123!");
    }

    @Test
    @DisplayName("내 정보 수정 실패 - 현재 비밀번호가 틀리면 비밀번호를 변경할 수 없다")
    void updateMyInfo_fail_invalidCurrentPassword() {
        // given
        String userId = "user-1";
        UserDto.UpdateRequest request = new UserDto.UpdateRequest("tester", "wrong-pass", "new123!");

        UserEntity user = UserEntity.builder()
                .id(userId)
                .password("encodedRealPassword")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 현재 비밀번호 확인 로직 Mocking: 틀렸으므로 false 반환
        when(passwordEncoder.matches("wrong-pass", "encodedRealPassword")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.updateMyInfo(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");

        // 비밀번호가 틀렸으므로 새로운 비밀번호를 암호화하는 로직은 절대 호출되면 안 됨
        verify(passwordEncoder, never()).encode(anyString());
    }
}