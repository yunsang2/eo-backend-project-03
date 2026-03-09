package com.example.chat.domain.user;

import com.example.chat.security.CustomUserDetails;
import com.example.chat.domain.user.user_enum.UserRole;

public class UserMapper {

    // 토큰에서 추출한 정보로 CustomUserDetails를 생성합니다.
    public static CustomUserDetails toCustomUserDetails(String username, UserRole role) {
        // 비밀번호는 토큰에 없으므로 보안상 "N/A" (또는 빈 문자열)로 처리합니다.
        return new CustomUserDetails(username, "N/A", role);
    }
}