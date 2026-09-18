package com.shubh.movietickets.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/* Typed DTO matching the PaymentCallback sent by Payment Service.
   Using a concrete class instead of Map<String, Object> gives us:
   - Compile-time safety (no more .get("bookingId").toString())
   - Jackson deserialisation error on malformed payloads
   - Clear API contract documentation */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCallbackRequest {
    private UUID paymentId;
    private UUID bookingId;
    private String status;      // "SUCCESS", "FAILED", etc.
    private BigDecimal amount;
    private String failureReason;
}
