package com.shubh.movietickets.tickets.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Schedule ID is required")
    private UUID scheduleId;

    @NotEmpty(message = "At least one seat is required")
    private List<String> seatNumbers;
}
