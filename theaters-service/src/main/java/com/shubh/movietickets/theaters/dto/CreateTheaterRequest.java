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
public class CreateTheaterRequest {

    @NotBlank(message = "Theater name is required")
    private String name;

    @NotBlank(message = "City is required")
    private String city;

    private String address;

    @NotEmpty(message = "At least one screen is required")
    @Valid
    private List<CreateScreenRequest> screens;
}
