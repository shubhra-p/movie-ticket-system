package com.shubh.movietickets.payment.service;

import com.shubh.movietickets.payment.dto.MockConfig;
import com.shubh.movietickets.payment.dto.PaymentRequest;
import com.shubh.movietickets.payment.dto.PaymentResponse;
import com.shubh.movietickets.payment.entity.Payment;
import com.shubh.movietickets.payment.entity.PaymentStatus;
import com.shubh.movietickets.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RestClient.Builder restClientBuilder;

    @InjectMocks
    private PaymentService paymentService;

    private UUID bookingId;
    private UUID userId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        userId = UUID.randomUUID();
        idempotencyKey = "booking-" + bookingId;
    }

    @Test
    @DisplayName("Initiate payment creates PENDING record")
    void initiatePayment_newPayment() {
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .id(UUID.randomUUID()).idempotencyKey(idempotencyKey).bookingId(bookingId)
                .userId(userId).amount(BigDecimal.valueOf(500)).currency("INR")
                .status(PaymentStatus.PENDING).build();
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        PaymentRequest request = new PaymentRequest(idempotencyKey, bookingId, userId,
                BigDecimal.valueOf(500), "INR", null);
        PaymentResponse response = paymentService.initiatePayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Idempotent: same key returns existing payment without creating new")
    void initiatePayment_idempotent() {
        Payment existing = Payment.builder()
                .id(UUID.randomUUID()).idempotencyKey(idempotencyKey).bookingId(bookingId)
                .userId(userId).amount(BigDecimal.valueOf(500)).currency("INR")
                .status(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        PaymentRequest request = new PaymentRequest(idempotencyKey, bookingId, userId,
                BigDecimal.valueOf(500), "INR", null);
        PaymentResponse response = paymentService.initiatePayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refund only works on successful payments")
    void refund_onlySuccessful() {
        Payment pending = Payment.builder()
                .id(UUID.randomUUID()).status(PaymentStatus.PENDING).build();
        when(paymentRepository.findById(any())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> paymentService.refund(UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("only refund successful");
    }

    @Test
    @DisplayName("Mock config updates atomically")
    void mockConfig_update() {
        paymentService.updateMockConfig(new MockConfig(30, 2000, true));
        MockConfig config = paymentService.getMockConfig();

        assertThat(config.getFailureRate()).isEqualTo(30);
        assertThat(config.getProcessingDelayMs()).isEqualTo(2000);
        assertThat(config.isForceNextFailure()).isTrue();
    }
}
