package com.shubh.movietickets.tickets.dto;

import com.shubh.movietickets.tickets.entity.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDto {
    private UUID id;
    private UUID userId;
    private UUID scheduleId;
    private List<String> seatNumbers;
    private BigDecimal totalAmount;
    private BookingStatus status;
    private UUID paymentId;
    private String failureReason;
    private Instant lockExpiresAt;
    private Instant createdAt;
}
