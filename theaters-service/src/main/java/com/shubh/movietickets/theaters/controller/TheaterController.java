package com.shubh.movietickets.theaters.controller;

import com.shubh.movietickets.common.dto.ApiResponse;
import com.shubh.movietickets.theaters.dto.ScreenDto;
import com.shubh.movietickets.theaters.dto.TheaterDto;
import com.shubh.movietickets.theaters.dto.CreateTheaterRequest;
import com.shubh.movietickets.theaters.service.TheaterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/theaters")
@RequiredArgsConstructor
public class TheaterController {

    private final TheaterService theaterService;

    @PostMapping
    public ResponseEntity<ApiResponse<TheaterDto>> createTheater(
            @Valid @RequestBody CreateTheaterRequest request) {
        TheaterDto theater = theaterService.createTheater(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Theater created", theater));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TheaterDto>>> getAllTheaters(
            @RequestParam(required = false) String city) {
        List<TheaterDto> theaters = (city != null)
                ? theaterService.getTheatersByCity(city)
                : theaterService.getAllTheaters();
        return ResponseEntity.ok(ApiResponse.success(theaters));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TheaterDto>> getTheater(@PathVariable UUID id) {
        TheaterDto theater = theaterService.getTheater(id);
        return ResponseEntity.ok(ApiResponse.success(theater));
    }

    @GetMapping("/screens/{screenId}")
    public ResponseEntity<ApiResponse<ScreenDto>> getScreen(@PathVariable UUID screenId) {
        ScreenDto screen = theaterService.getScreen(screenId);
        return ResponseEntity.ok(ApiResponse.success(screen));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Theaters Service is running"));
    }
}
