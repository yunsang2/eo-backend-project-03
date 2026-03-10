package com.example.chat.domain.user;

import com.example.chat.security.CustomUserDetails;
import com.example.chat.domain.user.user_enum.UserRole;

public class UserMapper {

    public static CustomUserDetails toCustomUserDetails(String userId, String username, UserRole role) {
        return new CustomUserDetails(userId, username, "N/A", role);
    }
}