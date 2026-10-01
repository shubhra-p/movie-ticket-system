package com.shubh.movietickets.payment.controller;

import com.shubh.movietickets.common.dto.ApiResponse;
import com.shubh.movietickets.payment.dto.MockConfig;
import com.shubh.movietickets.payment.dto.PaymentRequest;
import com.shubh.movietickets.payment.dto.PaymentResponse;
import com.shubh.movietickets.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Payment initiated", response));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(paymentId)));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByBookingId(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByBookingId(bookingId)));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment refunded", paymentService.refund(paymentId)));
    }

    /* Admin endpoints for mock behavior control */
    @PutMapping("/mock/config")
    public ResponseEntity<ApiResponse<MockConfig>> updateConfig(@Valid @RequestBody MockConfig config) {
        paymentService.updateMockConfig(config);
        return ResponseEntity.ok(ApiResponse.success("Mock config updated", config));
    }

    @GetMapping("/mock/config")
    public ResponseEntity<ApiResponse<MockConfig>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getMockConfig()));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Payment Service is running"));
    }
}
