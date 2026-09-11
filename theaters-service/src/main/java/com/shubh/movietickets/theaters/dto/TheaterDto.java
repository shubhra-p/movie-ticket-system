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
public class TheaterDto {
    private UUID id;
    private String name;
    private String city;
    private String address;
    private List<ScreenDto> screens;
}
