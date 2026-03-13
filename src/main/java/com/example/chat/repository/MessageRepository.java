package com.example.chat.repository;

import com.example.chat.domain.chat.message.MessageEntity;
import com.example.chat.domain.chat.session.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    // 특정 채팅방의 모든 메시지를 조회
    List<MessageEntity> findAllBySessionOrderByCreatedAtAsc(SessionEntity session);


    // 모델별 사용량 집계
    @Query("SELECT m.modelName AS modelName, SUM(m.usedTokens) AS totalUsedTokens " +
        "FROM MessageEntity m " +
        "GROUP BY m.modelName")
    List<ModelUsageProjection> sumTokensByModel();

    interface ModelUsageProjection {
        String getModelName();
        Long getTotalUsedTokens();
    }
}
