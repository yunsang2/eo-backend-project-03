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

    // application.yml에 설정한 Iamport API Key와 Secret을 주입받아 클라이언트를 초기화
    public PaymentService(
            @Value("${iamport.api.key}") String apiKey,
            @Value("${iamport.api.secret}") String apiSecret,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PlanRepository planRepository) {
        this.iamportClient = new IamportClient(apiKey, apiSecret);
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
    }

    /**
     * 포트원 결제 내역을 검증하고, 유저의 플랜을 업그레이드합니다.
     */
    @Transactional
    public PaymentDto.VerificationResponse verifyAndProcessPayment(String userId, PaymentDto.VerificationRequest request) {
        try {
            // 포트원 서버에서 실제 결제 내역 조회 (impUid 사용)
            IamportResponse<Payment> iamportResponse = iamportClient.paymentByImpUid(request.impUid());

            if (iamportResponse.getResponse() == null) {
                throw new IllegalArgumentException("포트원 서버에 존재하지 않는 결제 내역입니다.");
            }

            // 결제 정보 검증
            Payment iamportPayment = iamportResponse.getResponse();

            // DB에서 사용자가 결제하려던 플랜 정보를 추출
            PlanEntity targetPlan = planRepository.findById(request.planId())
                    .orElseThrow(() -> new NoSuchElementException("해당 플랜을 찾을 수 없습니다."));

            // 포트원에서 실제로 결제된 금액
            int actualPaidAmount = iamportPayment.getAmount().intValue();

            // 검증 로직: 프론트가 보낸 금액 == 포트원 실제 결제 금액 == DB 플랜 가격
            if (actualPaidAmount != request.amount() || actualPaidAmount != targetPlan.getPrice()) {
                // 금액이 다를시 예외 발생
                throw new IllegalArgumentException("결제 금액이 위변조되었습니다. 검증에 실패했습니다.");
            }

            // 포트원에서 결제하려는 사용자
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

            // 검증 성공 시 결제 내역 DB 저장
            PaymentEntity paymentEntity = PaymentEntity.builder()
                    .userId(user)
                    .planId(targetPlan)
                    .amount(actualPaidAmount)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            paymentRepository.save(paymentEntity);

            // UserEntity 내부에 구현한 업그레이드 메서드 호출
            user.upgradePlan(targetPlan);

            return new PaymentDto.VerificationResponse(
                    paymentEntity.getId(),
                    targetPlan.getName(),
                    "COMPLETED"
            );

        } catch (IamportResponseException | IOException e) {
            log.error("포트원 결제 검증 중 오류 발생: {}", e.getMessage());
            throw new RuntimeException("결제 검증 통신에 실패했습니다. 관리자에게 문의하세요.");
        }
    }
}