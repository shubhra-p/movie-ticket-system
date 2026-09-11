package com.shubh.movietickets.movies.controller;

import com.shubh.movietickets.common.dto.ApiResponse;
import com.shubh.movietickets.movies.dto.*;
import com.shubh.movietickets.movies.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @PostMapping
    public ResponseEntity<ApiResponse<MovieDto>> createMovie(
            @Valid @RequestBody CreateMovieRequest request) {
        MovieDto movie = movieService.createMovie(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Movie created", movie));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MovieDto>>> getAllMovies(
            @RequestParam(required = false) String search) {
        List<MovieDto> movies = (search != null)
                ? movieService.searchMovies(search)
                : movieService.getAllMovies();
        return ResponseEntity.ok(ApiResponse.success(movies));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MovieDto>> getMovie(@PathVariable UUID id) {
        MovieDto movie = movieService.getMovie(id);
        return ResponseEntity.ok(ApiResponse.success(movie));
    }

    @PostMapping("/schedules")
    public ResponseEntity<ApiResponse<ScheduleDto>> createSchedule(
            @Valid @RequestBody CreateScheduleRequest request) {
        ScheduleDto schedule = movieService.createSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Schedule created", schedule));
    }

    @GetMapping("/{movieId}/schedules")
    public ResponseEntity<ApiResponse<List<ScheduleDto>>> getSchedulesForMovie(
            @PathVariable UUID movieId) {
        List<ScheduleDto> schedules = movieService.getSchedulesForMovie(movieId);
        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @GetMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleDto>> getSchedule(
            @PathVariable UUID scheduleId) {
        ScheduleDto schedule = movieService.getSchedule(scheduleId);
        return ResponseEntity.ok(ApiResponse.success(schedule));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Movies Service is running"));
    }
}
