package com.shubh.movietickets.theaters.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateScreenRequest {

    @NotBlank(message = "Screen name is required")
    private String name;

    @NotEmpty(message = "At least one seat layout section is required")
    @Valid
    private List<SeatLayoutRequest> seatLayouts;
}
