package com.shubh.movietickets.payment.dto;

import com.shubh.movietickets.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/* Sent to the callbackUrl after async payment processing completes */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallback {
    private UUID paymentId;
    private UUID bookingId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String failureReason;
}
