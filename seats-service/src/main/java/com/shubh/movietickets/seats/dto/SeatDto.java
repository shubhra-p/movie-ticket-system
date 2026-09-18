package com.shubh.movietickets.seats.dto;

import com.shubh.movietickets.seats.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {
    private UUID id;
    private String seatNumber;
    private String category;
    private BigDecimal price;
    private SeatStatus status;
}
