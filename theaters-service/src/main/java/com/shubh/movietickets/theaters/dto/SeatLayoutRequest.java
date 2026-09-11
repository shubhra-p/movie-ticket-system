package com.shubh.movietickets.theaters.dto;

import com.shubh.movietickets.theaters.entity.SeatCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatLayoutRequest {

    @NotNull(message = "Seat category is required")
    private SeatCategory category;

    @NotBlank(message = "Row prefix is required (e.g., 'A' or 'F')")
    private String rowPrefix;

    @Min(value = 1, message = "Row count must be at least 1")
    private int rowCount;

    @Min(value = 1, message = "Seats per row must be at least 1")
    private int seatsPerRow;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;
}
