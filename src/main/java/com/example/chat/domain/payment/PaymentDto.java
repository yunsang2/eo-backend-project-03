package com.example.chat.domain.payment;

public class PaymentDto {

    /**
     * 프론트에서 결제 완료 후 검증을 위해 보내는 요청
     */
    public record VerificationRequest(
            // 포트원 결제 번호
            String impUid,
            // 서버에서 생성한 고유 번호
            String merchantUid,
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
