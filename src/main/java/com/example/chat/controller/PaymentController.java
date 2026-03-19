package com.example.chat.controller;

import com.example.chat.domain.ApiResponseDto;
import com.example.chat.domain.payment.PaymentDto;
import com.example.chat.security.CustomUserDetails;
import com.example.chat.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 포트원 결제 완료 후 서버 검증 및 플랜 업그레이드 API
     * POST /api/payments/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponseDto<PaymentDto.VerificationResponse>> verifyPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PaymentDto.VerificationRequest request) {

        log.info("💳 결제 검증 요청 수신: imp_uid={}, planName={}, amount={}",
                request.impUid(), request.planName(), request.amount());

        // 유저 인증 정보 확인
        if (userDetails == null) {
            log.error("❌ 결제 검증 실패: 로그인이 필요합니다.");
            return ResponseEntity.status(401).body(ApiResponseDto.fail("로그인 후 이용 가능합니다."));
        }

        // 결제 서비스 호출 (검증 및 토큰 충전)
        PaymentDto.VerificationResponse response = paymentService.verifyAndProcessPayment(
                userDetails.getId(), request);

        return ResponseEntity.ok(ApiResponseDto.success("결제 검증 및 플랜 업그레이드가 완료되었습니다.", response));
    }
}