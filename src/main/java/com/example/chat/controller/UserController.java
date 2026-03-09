package com.example.chat.controller;

import com.example.chat.domain.ApiResponseDto;
import com.example.chat.domain.user.UserDto;
import com.example.chat.domain.user.jwt.JwtDto;
import com.example.chat.security.CustomUserDetails;
import com.example.chat.service.user.MailService;
import com.example.chat.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MailService mailService;

    /**
     * 회원가입 API
     * POST /api/users/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponseDto<UserDto.Response>> signup(@Valid @RequestBody UserDto.SignUpRequest request) {

        // 서비스에서 회원가입 비즈니스 로직 처리
        UserDto.Response response = userService.registerUser(request);

        // 201 Created 상태 코드와 함께 통일된 규격(ApiResponseDto)으로 응답
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("회원가입이 완료되었습니다.", response));
    }

    /**
     * 메일 발송 API
     * POST /api/users/send-mail
     */
    @PostMapping("/send-mail")
    public ResponseEntity<ApiResponseDto<Void>> sendVerificationMail(@RequestParam String email) {
        // 서비스에서 메일 발송 비즈니스 로직 처리
        mailService.sendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponseDto.success("인증 메일이 발송되었습니다."));
    }

    /**
     * 인증 확인 API
     * POST /api/users/verify-mail
     */
    @PostMapping("/verify-mail")
    public ResponseEntity<ApiResponseDto<Void>> verifyMail(@RequestParam String email, @RequestParam String code) {
        // 서비스에서 인증 확인 비즈니스 로직 처리
        mailService.verifyCode(email, code);
        return ResponseEntity.ok(ApiResponseDto.success("이메일 인증이 완료되었습니다."));
    }

    /**
     * 로그인 API (JWT 발급)
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<JwtDto.Response>> login(@Valid @RequestBody UserDto.LoginRequest request) {

        // 서비스에서 이메일/비밀번호 검증 후 Access/Refresh 토큰 세트 받아오기
        JwtDto.Response tokenResponse = userService.login(request);

        // 200 OK 상태 코드와 함께 토큰 응답
        return ResponseEntity.ok(
                ApiResponseDto.success("로그인 성공!", tokenResponse)
        );
    }

    /**
     * 회원 탈퇴 API
     * DELETE /api/users/withdraw
     */
    @DeleteMapping("/withdraw")
    public ResponseEntity<ApiResponseDto<Void>> withdraw(
            // JWT 토큰에서 현재 로그인한 유저 정보를 추출
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 토큰에 들어있던 정보를 서비스로 넘겨서 탈퇴 처리
        userService.withdrawUser(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponseDto.success("회원 탈퇴가 완료되었습니다."));
    }


    /**
     * 재설정 인증 API
     * POST /api/users/password/forgot
     */
    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponseDto<Void>> forgotPassword(@Valid @RequestBody UserDto.PasswordForgotRequest request) {
        userService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponseDto.success("비밀번호 재설정 메일이 발송되었습니다. 메일함을 확인해주세요."));
    }

    /**
     * 비번 변경 API
     * POST /api/users/password/reset
     */
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponseDto<Void>> resetPassword(@Valid @RequestBody UserDto.PasswordResetRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.ok(ApiResponseDto.success("비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해주세요."));
    }
}