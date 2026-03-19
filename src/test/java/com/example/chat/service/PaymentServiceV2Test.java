package com.example.chat.service;

import com.example.chat.domain.payment.PaymentEntity;
import com.example.chat.domain.payment.PaymentDto;
import com.example.chat.domain.payment.PaymentStatus;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.PaymentRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class PaymentServiceV2Test {

    @InjectMocks
    private PaymentService paymentService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private RestTemplate restTemplate;

    private UserEntity testUser;
    private PlanEntity proPlan;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "apiSecret", "test-v2-secret-key");

        proPlan = PlanEntity.builder()
                .id("plan-pro")
                .name("PRO")
                .price(9900)
                .build();

        testUser = UserEntity.builder()
                .id("user-123")
                .email("test@test.com")
                .remainingTokens(5000)
                .build();
    }

    @Test
    @DisplayName("성공: [V2] API 응답이 PAID이고 금액이 일치하면 결제 정보를 저장하고 플랜을 승급한다")
    void verifyAndProcessPayment_V2_Success() throws Exception {
        // Given
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "payment-12345", "PRO", 9900
        );

        String mockJsonResponse = """
                {
                    "status": "PAID",
                    "amount": {
                        "total": 9900
                    }
                }
                """;

        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(mockJsonResponse, HttpStatus.OK);

        given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .willReturn(mockResponseEntity);
        given(planRepository.findByName("PRO")).willReturn(Optional.of(proPlan));
        given(userRepository.findById("user-123")).willReturn(Optional.of(testUser));

        // When
        PaymentDto.VerificationResponse response = paymentService.verifyAndProcessPayment("user-123", request);

        // Then
        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.planName()).isEqualTo("PRO");

        verify(paymentRepository, times(1)).save(any(PaymentEntity.class));
        log.info("결제 성공 테스트 완료");
    }

    @Test
    @DisplayName("실패: [V2] 결제 상태가 PAID가 아닌 경우(READY) 예외가 발생한다")
    void verifyAndProcessPayment_V2_Fail_NotPaid() throws Exception {
        // Given
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "payment-12345", "PRO", 9900
        );

        String mockJsonResponse = """
                {
                    "status": "READY",
                    "amount": {
                        "total": 9900
                    }
                }
                """;
        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(mockJsonResponse, HttpStatus.OK);

        given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .willReturn(mockResponseEntity);

        // When & Then
        // 🚨 이제 RuntimeException이 아닌 IllegalArgumentException으로 정확히 던져집니다.
        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제가 완료되지 않았습니다");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("실패: [V2] 실제 결제 금액(100원)이 플랜 가격(9900원)과 다르면 위변조로 판단한다")
    void verifyAndProcessPayment_V2_Fail_AmountMismatch() throws Exception {
        // Given
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "payment-12345", "PRO", 9900
        );

        String mockJsonResponse = """
                {
                    "status": "PAID",
                    "amount": {
                        "total": 100
                    }
                }
                """;
        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(mockJsonResponse, HttpStatus.OK);

        given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .willReturn(mockResponseEntity);
        given(planRepository.findByName("PRO")).willReturn(Optional.of(proPlan));

        // When & Then
        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액이 플랜 가격과 일치하지 않습니다");
    }

    @Test
    @DisplayName("실패: [V2] 존재하지 않는 플랜 이름으로 요청 시 NoSuchElementException이 발생한다")
    void verifyAndProcessPayment_V2_Fail_PlanNotFound() throws Exception {
        // Given
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "payment-12345", "INVALID_PLAN", 9900
        );

        String mockJsonResponse = """
                {
                    "status": "PAID",
                    "amount": {
                        "total": 9900
                    }
                }
                """;
        ResponseEntity<String> mockResponseEntity = new ResponseEntity<>(mockJsonResponse, HttpStatus.OK);

        given(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .willReturn(mockResponseEntity);
        given(planRepository.findByName("INVALID_PLAN")).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("user-123", request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("해당 플랜을 찾을 수 없습니다");
    }
}