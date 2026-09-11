package com.shubh.movietickets.theaters.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenDto {
    private UUID id;
    private String name;
    private int totalSeats;
    private List<SeatLayoutDto> seatLayouts;
}
