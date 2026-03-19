package com.example.chat.service;

import com.example.chat.domain.payment.PaymentEntity;
import com.example.chat.domain.payment.PaymentDto;
import com.example.chat.domain.payment.PaymentStatus;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.PaymentRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.NoSuchElementException;

@Slf4j
@Service
public class PaymentService {

    private final IamportClient iamportClient;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    public PaymentService(
            @Value("${iamport.api.key}") String apiKey,
            @Value("${iamport.api.secret}") String apiSecret,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PlanRepository planRepository) {

        log.info("💳 포트원 클라이언트 초기화 중...");
        this.iamportClient = new IamportClient(apiKey, apiSecret);
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
    }

    /**
     * 결제 검증 및 플랜 업그레이드
     */
    @Transactional
    public PaymentDto.VerificationResponse verifyAndProcessPayment(String userId, PaymentDto.VerificationRequest request) {
        try {
            log.info("💳 결제 검증 시작 - imp_uid: {}, planName: {}", request.impUid(), request.planName());

            // 1. 포트원 서버 내역 조회
            IamportResponse<Payment> iamportResponse = iamportClient.paymentByImpUid(request.impUid());

            if (iamportResponse == null || iamportResponse.getResponse() == null) {
                log.error("❌ 결제 내역 조회 실패");
                throw new IllegalArgumentException("존재하지 않는 결제 번호입니다.");
            }

            Payment iamportPayment = iamportResponse.getResponse();

            // 2. 플랜 정보 조회
            PlanEntity targetPlan = planRepository.findByName(request.planName())
                    .orElseThrow(() -> new NoSuchElementException("해당 플랜을 찾을 수 없습니다: " + request.planName()));

            // 3. 금액 검증 (PlanEntity 필드: price)
            int actualPaidAmount = iamportPayment.getAmount().intValue();
            if (actualPaidAmount != request.amount() || actualPaidAmount != targetPlan.getPrice()) {
                log.error("❌ 금액 불일치! 실제: {}, 요청: {}, DB가격: {}",
                        actualPaidAmount, request.amount(), targetPlan.getPrice());
                throw new IllegalArgumentException("결제 금액 위변조가 감지되었습니다.");
            }

            // 4. 유저 조회
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

            // 5. 결제 내역 DB 저장
            // 🚨 해결: 엔티티의 필드명이 userId, planId이지만 타입이 객체이므로 객체(user, targetPlan)를 전달합니다.
            PaymentEntity paymentEntity = PaymentEntity.builder()
                    .user(user)
                    .plan(targetPlan)
                    .amount(actualPaidAmount)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            paymentRepository.save(paymentEntity);

            // 6. 유저 플랜 업그레이드
            user.upgradePlan(targetPlan);
            log.info("✅ 결제 성공 및 플랜 업그레이드 완료: {}", user.getEmail());

            // 🚨 record 타입 DTO는 new 생성자로 반환합니다.
            return new PaymentDto.VerificationResponse(
                    paymentEntity.getId(),
                    targetPlan.getName(),
                    "COMPLETED"
            );

        } catch (IamportResponseException e) {
            log.error("❌ 포트원 API 에러: {}", e.getMessage());
            throw new RuntimeException("포트원 인증 실패");
        } catch (IOException e) {
            log.error("❌ 통신 에러: {}", e.getMessage());
            throw new RuntimeException("결제 서버 통신 실패");
        }
    }

    /**
     * 관리자용 매출 조회
     */
    public long getTotalSales() {
        return paymentRepository.sumTotalCompletedAmount().orElse(0L);
    }
}