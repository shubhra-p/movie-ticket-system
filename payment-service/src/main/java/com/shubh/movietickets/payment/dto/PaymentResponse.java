package com.shubh.movietickets.payment.dto;

import com.shubh.movietickets.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID paymentId;
    private UUID bookingId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String failureReason;
    private Instant createdAt;
}
