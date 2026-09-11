package com.shubh.movietickets.movies.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDto {
    private UUID id;
    private String title;
    private String description;
    private String genre;
    private int durationMinutes;
    private String language;
    private String posterUrl;
    private LocalDate releaseDate;
}
