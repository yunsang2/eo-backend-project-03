package com.example.chat.service;

import com.example.chat.domain.payment.PaymentEntity;
import com.example.chat.domain.payment.PaymentDto;
import com.example.chat.domain.payment.PaymentStatus;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.PaymentRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.NoSuchElementException;

/**
 * PortOne V2 API를 사용하여 결제 검증 및 처리를 담당하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final RestTemplate restTemplate;

    /**
     * ObjectMapper 빈 주입 에러 방지를 위해 직접 초기화하며, JavaTimeModule을 등록합니다.
     */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Value("${portone.api.secret}")
    private String apiSecret;

    // 포트원 V2 결제 단건 조회 API 엔드포인트
    private static final String PORTONE_V2_API_URL = "https://api.portone.io/payments/";

    /**
     * 포트원 V2 결제 검증 및 플랜 업그레이드 로직입니다.
     */
    @Transactional
    public PaymentDto.VerificationResponse verifyAndProcessPayment(String userId, PaymentDto.VerificationRequest request) {
        try {
            log.info("💳 [V2] 결제 검증 시작 - PaymentId: {}, PlanName: {}", request.paymentId(), request.planName());

            // 1. 포트원 V2 API 호출 (헤더에 PortOne Secret 설정)
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "PortOne " + apiSecret);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    PORTONE_V2_API_URL + request.paymentId(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("❌ 포트원 서버 응답 에러: {}", response.getStatusCode());
                throw new RuntimeException("포트원 서버에서 결제 정보를 가져올 수 없습니다.");
            }

            // 2. JSON 응답 파싱 및 상태/금액 검증
            JsonNode root = objectMapper.readTree(response.getBody());
            String status = root.path("status").asText(); // PAID, READY, FAILED, CANCELLED 등
            int actualPaidAmount = root.path("amount").path("total").asInt();

            log.info("🔍 포트원 응답 상태: {}, 실제 결제 금액: {}", status, actualPaidAmount);

            if (!"PAID".equals(status)) {
                throw new IllegalArgumentException("결제가 완료되지 않았습니다.");
            }

            // 3. 비즈니스 검증 (DB 플랜 정보와 대조)
            PlanEntity targetPlan = planRepository.findByName(request.planName())
                    .orElseThrow(() -> new NoSuchElementException("해당 플랜을 찾을 수 없습니다: " + request.planName()));

            if (actualPaidAmount != targetPlan.getPrice()) {
                log.error("❌ 금액 위변조 감지! 실제: {}, DB가격: {}", actualPaidAmount, targetPlan.getPrice());
                throw new IllegalArgumentException("결제 금액이 플랜 가격과 일치하지 않습니다.");
            }

            // 4. 유저 조회
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

            // 5. 결제 내역 저장 (PaymentEntity의 @ManyToOne 관계 반영)
            PaymentEntity paymentEntity = PaymentEntity.builder()
                    .paymentId(request.paymentId())
                    .user(user)        // UserEntity 객체 직접 전달
                    .plan(targetPlan)  // PlanEntity 객체 직접 전달
                    .amount(actualPaidAmount)
                    .status(PaymentStatus.COMPLETED)
                    .build();
            paymentRepository.save(paymentEntity);

            // 6. 유저 플랜 업그레이드 로직 실행 (토큰 리셋 등)
            user.upgradePlan(targetPlan);
            log.info("✅ [V2] 결제 성공 및 유저 플랜 승급 완료: {}", user.getEmail());

            // Record 타입 DTO 반환
            return new PaymentDto.VerificationResponse(
                    paymentEntity.getPaymentId(),
                    targetPlan.getName(),
                    "PAID"
            );

        } catch (IllegalArgumentException | NoSuchElementException e) {
            // 비즈니스 예외는 그대로 던져서 테스트 통과 및 GlobalExceptionHandler에서 400/404 처리가 가능하게 함
            throw e;
        } catch (Exception e) {
            log.error("❌ [V2] 결제 처리 중 예상치 못한 예외 발생: {}", e.getMessage());
            throw new RuntimeException("결제 검증 중 서버 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 관리자용 매출 통계 조회
     */
    public long getTotalSales() {
        return paymentRepository.sumTotalCompletedAmount().orElse(0L);
    }
}