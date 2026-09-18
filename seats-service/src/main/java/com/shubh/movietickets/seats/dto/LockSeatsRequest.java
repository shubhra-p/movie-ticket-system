package com.shubh.movietickets.seats.dto;

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
public class LockSeatsRequest {

    @NotNull(message = "Schedule ID is required")
    private UUID scheduleId;

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotEmpty(message = "At least one seat number is required")
    private List<String> seatNumbers;
}
