package com.shubh.movietickets.theaters.dto;

import com.shubh.movietickets.theaters.entity.SeatCategory;
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
public class SeatLayoutDto {
    private UUID id;
    private SeatCategory category;
    private String rowPrefix;
    private int rowCount;
    private int seatsPerRow;
    private BigDecimal price;
}
