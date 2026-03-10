package com.example.chat.security.jwt;

import com.example.chat.domain.user.UserDto;
import com.example.chat.domain.user.UserMapper;
import com.example.chat.domain.user.user_enum.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveTokenFromCookie(request);

        if (token == null) {
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                token = header.substring(7);
            } else {
                filterChain.doFilter(request, response);
                return;
            }
        }

        try {
            if (jwtProvider.isExpired(token)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The token has expired");
                return;
            }

            String userId = jwtProvider.getUserId(token);
            String username = jwtProvider.getUsername(token);
            UserRole userRole = UserRole.valueOf(
                    jwtProvider.getAuthority(token).replace("ROLE_", "")
            );

            UserDetails userDetails = UserMapper.toCustomUserDetails(userId, username, userRole);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("accessToken")) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}