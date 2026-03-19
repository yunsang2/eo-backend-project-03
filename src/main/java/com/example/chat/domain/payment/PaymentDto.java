package com.example.chat.domain.payment;

public class PaymentDto {

    /**
     * 프론트에서 결제 완료 후 검증을 위해 보내는 요청
     */
    public record VerificationRequest(
            // 포트원 V2 결제 고유 ID
            String paymentId,
            // 결제하려는 플랜
            String planName,
            // 결제한 금액
            int amount
    ) {}

    /**
     * 백엔드에서 검증후 프론트로 보내는 응답
     */
    public record VerificationResponse(
            String paymentId,
            String planName,
            String status
    ) {}
}
