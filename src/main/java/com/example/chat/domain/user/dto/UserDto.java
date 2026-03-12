package com.example.chat.domain.user.dto;

import com.example.chat.domain.user.UserEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UserDto {


    // 회원가입 요청 Dto
    public record SignUpRequest(
            @NotBlank(message = "이메일은 필수 입력값입니다.")
            @Email(message = "올바른 이메일 형식이 아닙니다.")
            String email,

            @NotBlank(message = "비밀번호는 필수 입력값입니다.")
            @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z])(?=.*\\W)(?=\\S+$).{8,16}", message = "비밀번호는 8~16자 영문 대 소문자, 숫자, 특수문자를 사용하세요.")
            String password,

            @NotBlank(message = "유저네임(아이디)은 필수 입력값입니다.")
            String username,

            @NotBlank(message = "이름은 필수 입력값입니다.")
            String name
    ) {}


    // 로그인 요청 Dto
    public record LoginRequest(
            @NotBlank(message = "이메일을 입력해주세요.")
            String email,

            @NotBlank(message = "비밀번호를 입력해주세요.")
            String password
    ) {}


    // 비밀번호 찾기 요청 Dto (이메일만 입력)
    public record PasswordForgotRequest(
            @NotBlank(message = "가입하신 이메일을 입력해주세요.")
            String email
    ) {}

    // 비밀번호 재설정 요청 (메일로 받은 토큰 + 새 비밀번호)
    public record PasswordResetRequest(
            @NotBlank(message = "잘못된 접근입니다. (토큰 누락)")
            String resetToken,

            @NotBlank(message = "새로운 비밀번호를 입력해주세요.")
            @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z])(?=.*\\W)(?=\\S+$).{8,16}", message = "비밀번호는 8~16자 영문 대 소문자, 숫자, 특수문자를 사용하세요.")
            String newPassword
    ) {}


    // 사용자 정보 업데이트 요청 Dto
    public record UpdateRequest(
            @NotBlank(message = "변경할 이름을 입력해주세요.")
            String username,

            //현재 비밀번호 확인용
            String currentPassword,

            @Pattern(regexp = "(?=.*[0-9])(?=.*[a-zA-Z])(?=.*\\W)(?=\\S+$).{8,16}",
                    message = "비밀번호는 8~16자 영문 대 소문자, 숫자, 특수문자를 사용하세요.")

            // 변경된 후 비밀번호
            String newPassword
    ) {}


    // 사용자 정보 응답 Dto
    public record Response(
            String id,
            String email,
            String username,
            String role,
            String status,
            String planId,
            String planName,
            // 잔여 토큰
            int remainingTokens
    ) {
        // Entity -> Dto
        public static Response fromEntity(UserEntity user) {
            return new Response(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getRole().name(),
                    user.getStatus().name(),
                    user.getPlan() != null ? user.getPlan().getId() : null,
                    user.getPlan() != null ? user.getPlan().getName() : "플랜 없음",
                    user.getRemainingTokens()
            );
        }
    }
}