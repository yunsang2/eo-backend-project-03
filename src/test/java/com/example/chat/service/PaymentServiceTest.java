package com.example.chat.service;

import com.example.chat.domain.payment.PaymentEntity;
import com.example.chat.domain.payment.PaymentDto;
import com.example.chat.domain.plan.PlanEntity;
import com.example.chat.domain.user.UserEntity;
import com.example.chat.repository.PaymentRepository;
import com.example.chat.repository.PlanRepository;
import com.example.chat.repository.user.UserRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentService paymentService;

    @Mock private IamportClient iamportClient;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;

    private UserEntity testUser;
    private PlanEntity proPlan;

    @BeforeEach
    void setUp() {
        // PaymentService 내부의 private 필드인 iamportClient를 Mock 객체로 교체 (생성자에서 new로 생성하기 때문)
        ReflectionTestUtils.setField(paymentService, "iamportClient", iamportClient);

        proPlan = PlanEntity.builder()
                .id("plan-pro")
                .name("PRO")
                .price(9900)
                .limitTokens(50000)
                .build();

        testUser = UserEntity.builder()
                .id("user-123")
                .email("test@test.com")
                .remainingTokens(5000)
                .build();
    }

    @Test
    @DisplayName("성공: 포트원 결제 검증이 완료되면 유저의 플랜이 업그레이드된다")
    void verifyAndProcessPayment_Success() throws Exception {
        // Given
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "imp_123456", "merchant_123", "plan-pro", 9900
        );

        // 포트원 응답 모킹
        Payment mockIamportPayment = mock(Payment.class);
        given(mockIamportPayment.getAmount()).willReturn(BigDecimal.valueOf(9900));

        IamportResponse<Payment> mockResponse = new IamportResponse<>();
        ReflectionTestUtils.setField(mockResponse, "response", mockIamportPayment);

        given(iamportClient.paymentByImpUid(anyString())).willReturn(mockResponse);

        // DB 조회 모킹
        given(planRepository.findById("plan-pro")).willReturn(Optional.of(proPlan));
        given(userRepository.findById("user-123")).willReturn(Optional.of(testUser));

        // When
        PaymentDto.VerificationResponse response = paymentService.verifyAndProcessPayment("user-123", request);

        // Then
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.planName()).isEqualTo("PRO");
        assertThat(testUser.getPlan().getName()).isEqualTo("PRO");
        assertThat(testUser.getRemainingTokens()).isEqualTo(50000);

        verify(paymentRepository, times(1)).save(any(PaymentEntity.class));
    }

    @Test
    @DisplayName("실패: 실제 결제 금액과 요청 금액이 다르면 예외가 발생한다 (위변조 차단)")
    void verifyAndProcessPayment_Fail_AmountMismatch() throws Exception {
        // Given
        // 프론트에서는 9900원 결제했다고 요청함
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "imp_123456", "merchant_123", "plan-pro", 9900
        );

        // 하지만 포트원 서버에는 실제 100원만 결제된 상태 (조작 시나리오)
        Payment mockIamportPayment = mock(Payment.class);
        given(mockIamportPayment.getAmount()).willReturn(BigDecimal.valueOf(100));

        IamportResponse<Payment> mockResponse = new IamportResponse<>();
        ReflectionTestUtils.setField(mockResponse, "response", mockIamportPayment);

        given(iamportClient.paymentByImpUid(anyString())).willReturn(mockResponse);
        given(planRepository.findById("plan-pro")).willReturn(Optional.of(proPlan));

        // When & Then
        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액이 위변조되었습니다");

        // DB 저장이 일어나지 않아야 함
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("실패: 포트원 서버에 해당 결제 건이 존재하지 않으면 예외가 발생한다")
    void verifyAndProcessPayment_Fail_NoPayment() throws Exception {
        // Given
        PaymentDto.VerificationRequest request = new PaymentDto.VerificationRequest(
                "invalid_imp", "merchant_123", "plan-pro", 9900
        );

        IamportResponse<Payment> mockResponse = new IamportResponse<>();
        // response 필드가 null인 상태

        given(iamportClient.paymentByImpUid(anyString())).willReturn(mockResponse);

        // When & Then
        assertThatThrownBy(() -> paymentService.verifyAndProcessPayment("user-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 결제 내역입니다");
    }
}