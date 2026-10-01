package com.shubh.movietickets.payment.service;

import com.shubh.movietickets.payment.dto.PaymentCallback;
import com.shubh.movietickets.payment.entity.Payment;
import com.shubh.movietickets.payment.entity.PaymentStatus;
import com.shubh.movietickets.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/*
 Separate component for async payment processing.
 Extracted from PaymentService to avoid Spring @Async self-invocation,
 which silently runs synchronously due to proxy bypass.

 When a bean calls its own @Async method via `this.method()`, the call
 bypasses the Spring AOP proxy and executes on the caller's thread.
 By placing the @Async method in a different bean, Spring's proxy
 correctly intercepts the call and dispatches it to the async executor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentAsyncProcessor {

    private final PaymentRepository paymentRepository;
    private final RestClient.Builder restClientBuilder;

    /* Thread-safe mock behavior controls */
    private final AtomicInteger failureRate = new AtomicInteger(0);
    private final AtomicLong processingDelayMs = new AtomicLong(1000);
    private final AtomicBoolean forceNextFailure = new AtomicBoolean(false);

    private static final int MAX_CALLBACK_RETRIES = 3;
    private static final long CALLBACK_RETRY_BASE_DELAY_MS = 500;

    /*
     Async payment processing: simulates a real payment gateway that
     takes time to process and then notifies via webhook.

     Runs on a separate thread (via Spring @Async proxy) to avoid
     blocking the HTTP response to the caller.
     */
    @Async
    @Transactional
    public void processPaymentAsync(UUID paymentId) {
        try {
            /* Simulate processing delay */
            long delay = processingDelayMs.get();
            if (delay > 0) {
                Thread.sleep(delay);
            }

            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
                return;
            }

            /* Determine outcome */
            boolean shouldFail = forceNextFailure.compareAndSet(true, false)
                    || (failureRate.get() > 0 && ThreadLocalRandom.current().nextInt(100) < failureRate.get());

            if (shouldFail) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Payment declined by issuing bank");
                log.info("Payment FAILED: paymentId={}, bookingId={}", paymentId, payment.getBookingId());
            } else {
                payment.setStatus(PaymentStatus.SUCCESS);
                log.info("Payment SUCCESS: paymentId={}, bookingId={}", paymentId, payment.getBookingId());
            }

            paymentRepository.save(payment);

            /* Send webhook callback to the caller with retry */
            if (payment.getCallbackUrl() != null && !payment.getCallbackUrl().isBlank()) {
                sendCallbackWithRetry(payment);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payment processing interrupted for paymentId={}", paymentId);
        } catch (Exception e) {
            log.error("Payment processing error for paymentId={}: {}", paymentId, e.getMessage());
        }
    }

    /*
     Send webhook callback with exponential backoff retry.
     In a production system this would use a persistent outbox + scheduler,
     but for a mock service, in-memory retry is sufficient.
     */
    private void sendCallbackWithRetry(Payment payment) {
        for (int attempt = 1; attempt <= MAX_CALLBACK_RETRIES; attempt++) {
            try {
                PaymentCallback callback = PaymentCallback.builder()
                        .paymentId(payment.getId())
                        .bookingId(payment.getBookingId())
                        .status(payment.getStatus())
                        .amount(payment.getAmount())
                        .failureReason(payment.getFailureReason())
                        .build();

                restClientBuilder.build()
                        .post()
                        .uri(payment.getCallbackUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(callback)
                        .retrieve()
                        .toBodilessEntity();

                log.info("Callback sent to {}: paymentId={}, status={} (attempt {})",
                        payment.getCallbackUrl(), payment.getId(), payment.getStatus(), attempt);
                return; // Success — exit retry loop

            } catch (Exception e) {
                log.warn("Callback attempt {}/{} failed for paymentId={}, url={}: {}",
                        attempt, MAX_CALLBACK_RETRIES, payment.getId(),
                        payment.getCallbackUrl(), e.getMessage());

                if (attempt < MAX_CALLBACK_RETRIES) {
                    try {
                        long backoff = CALLBACK_RETRY_BASE_DELAY_MS * (1L << (attempt - 1)); // 500ms, 1s, 2s
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.error("All {} callback attempts exhausted for paymentId={}, url={}. " +
                              "Payment status is {} but callback was not delivered.",
                            MAX_CALLBACK_RETRIES, payment.getId(),
                            payment.getCallbackUrl(), payment.getStatus());
                }
            }
        }
    }

    /* ── Mock config accessors ──────────────────────────────────── */

    public void setFailureRate(int rate) { failureRate.set(rate); }
    public void setProcessingDelayMs(long delay) { processingDelayMs.set(delay); }
    public void setForceNextFailure(boolean force) { forceNextFailure.set(force); }
    public int getFailureRate() { return failureRate.get(); }
    public long getProcessingDelayMs() { return processingDelayMs.get(); }
    public boolean getForceNextFailure() { return forceNextFailure.get(); }
}
