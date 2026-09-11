package com.shubh.movietickets.movies.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {
    private UUID id;
    private UUID movieId;
    private String movieTitle;
    private UUID screenId;
    private UUID theaterId;
    private String theaterName;
    private String screenName;
    private LocalDate showDate;
    private LocalTime showTime;
    private int totalSeats;
}
