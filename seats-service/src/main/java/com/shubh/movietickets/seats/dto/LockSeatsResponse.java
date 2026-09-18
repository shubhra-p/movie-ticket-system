package com.shubh.movietickets.seats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockSeatsResponse {
    private boolean locked;
    private List<String> seatNumbers;
    private BigDecimal totalAmount;
    private int lockTtlSeconds;
}
