package com.example.chat.repository.user;

import com.example.chat.domain.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    // 로그인시 이메일로 유저 찾기
    Optional<UserEntity> findByEmail(String email);

    // 이메일 중복확인
    boolean existsByEmail(String email);

    // 닉네임 중복확인
    boolean existsByUsername(String username);

    // 비밀번호 재설정 토큰으로 유저 찾기
    Optional<UserEntity> findByResetToken(String resetToken);

    @Query("SELECT p.name AS planName, COUNT(u) AS userCount " +
        "FROM UserEntity u JOIN u.plan p " +
        "GROUP BY p.name")
    List<PlanCountProjection> countUsersByPlan();

    // 결과를 담을 프로젝션 인터페이스
    interface PlanCountProjection {
        String getPlanName();
        Long getUserCount();
    }
}
