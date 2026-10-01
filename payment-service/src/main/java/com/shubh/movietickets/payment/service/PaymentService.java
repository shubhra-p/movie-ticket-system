package com.shubh.movietickets.payment.service;

import com.shubh.movietickets.payment.dto.MockConfig;
import com.shubh.movietickets.payment.dto.PaymentRequest;
import com.shubh.movietickets.payment.dto.PaymentResponse;
import com.shubh.movietickets.payment.entity.Payment;
import com.shubh.movietickets.payment.entity.PaymentStatus;
import com.shubh.movietickets.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAsyncProcessor asyncProcessor;

    /*
     Initiate a payment. This is a two-phase process:
       Phase 1 (sync): Validate, idempotency check, persist PENDING record, return 202 Accepted
       Phase 2 (async): Delegated to PaymentAsyncProcessor (separate bean for correct @Async proxy)
     */
    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        /* Idempotency: if a payment with this key already exists, return it */
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent hit for key={}, returning existing paymentId={}",
                    request.getIdempotencyKey(), existing.get().getId());
            return toResponse(existing.get());
        }

        Payment payment = Payment.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .bookingId(request.getBookingId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .callbackUrl(request.getCallbackUrl())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment initiated: paymentId={}, bookingId={}, amount={}",
                payment.getId(), payment.getBookingId(), payment.getAmount());

        /*
         Kick off async processing via a separate bean.
         Because @Async is proxy-based, calling an @Async method on `this`
         bypasses the proxy and runs synchronously. By delegating to
         PaymentAsyncProcessor, Spring's proxy correctly intercepts the call.
         */
        asyncProcessor.processPaymentAsync(payment.getId());

        return toResponse(payment);
    }

    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));
        return toResponse(payment);
    }

    public PaymentResponse getPaymentByBookingId(UUID bookingId) {
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Payment not found for booking: " + bookingId));
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new RuntimeException("Can only refund successful payments, current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        log.info("Payment REFUNDED: paymentId={}, bookingId={}", paymentId, payment.getBookingId());
        return toResponse(payment);
    }

    public void updateMockConfig(MockConfig config) {
        asyncProcessor.setFailureRate(config.getFailureRate());
        asyncProcessor.setProcessingDelayMs(config.getProcessingDelayMs());
        asyncProcessor.setForceNextFailure(config.isForceNextFailure());
        log.info("Mock config updated: failureRate={}%, delay={}ms, forceNextFailure={}",
                config.getFailureRate(), config.getProcessingDelayMs(), config.isForceNextFailure());
    }

    public MockConfig getMockConfig() {
        return new MockConfig(
                asyncProcessor.getFailureRate(),
                asyncProcessor.getProcessingDelayMs(),
                asyncProcessor.getForceNextFailure());
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
