package com.example.chat.repository;

import com.example.chat.domain.payment.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {

    // 특정 유저의 결제 내역 조회
    List<PaymentEntity> findAllByUserIdOrderByCreatedAtDesc(String user);

    // V2 규격에 맞춰 paymentId로 결제 내역을 조회
    Optional<PaymentEntity> findByPaymentId(String paymentId);

    // sumTotalCompletedAmount 메서드가 누락되어 발생한 에러를 해결
    @Query("SELECT SUM(p.amount) FROM PaymentEntity p WHERE p.status = 'COMPLETED'")
    Optional<Long> sumTotalCompletedAmount();
}
