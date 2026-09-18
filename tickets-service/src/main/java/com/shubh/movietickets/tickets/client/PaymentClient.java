package com.shubh.movietickets.tickets.client;

import com.shubh.movietickets.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments")
    ApiResponse<PaymentResponse> initiatePayment(@RequestBody PaymentRequest request);

    @GetMapping("/api/payments/{paymentId}")
    ApiResponse<PaymentResponse> getPayment(@PathVariable UUID paymentId);

    record PaymentRequest(String idempotencyKey, UUID bookingId, UUID userId,
                          BigDecimal amount, String currency, String callbackUrl) {}
    record PaymentResponse(UUID paymentId, UUID bookingId, String status,
                           BigDecimal amount, String failureReason) {}
}
