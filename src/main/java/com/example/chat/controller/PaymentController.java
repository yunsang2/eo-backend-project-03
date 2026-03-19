package com.example.chat.controller;

import com.example.chat.domain.ApiResponseDto;
import com.example.chat.domain.payment.PaymentDto;
import com.example.chat.security.CustomUserDetails;
import com.example.chat.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // application-secret.yml에 설정된 V2 키값들을 가져옵니다.
    @Value("${portone.api.store-id}")
    private String storeId;

    @Value("${portone.api.kakao-channel-key}")
    private String kakaoChannelKey;

    /**
     * [Config API] 프론트엔드에 결제에 필요한 Store ID와 Channel Key를 전달합니다.
     * 보안을 위해 클라이언트 코드에 하드코딩하지 않고 서버에서 내려주는 방식을 사용합니다.
     */
    @GetMapping("/config")
    public ResponseEntity<ApiResponseDto<Map<String, String>>> getPaymentConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("storeId", storeId);
        config.put("channelKey", kakaoChannelKey);

        log.info("📢 결제 설정 정보 요청 수신 (StoreId: {})", storeId);
        return ResponseEntity.ok(ApiResponseDto.success("결제 설정 로드 완료", config));
    }

    /**
     * 포트원 결제 완료 후 서버 검증 및 플랜 업그레이드 API
     * POST /api/payments/verify
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponseDto<PaymentDto.VerificationResponse>> verifyPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PaymentDto.VerificationRequest request) {

        log.info("💳 결제 검증 요청 수신: imp_uid={}, planName={}, amount={}",
                request.paymentId(), request.planName(), request.amount());

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