package com.shubh.movietickets.seats.controller;

import com.shubh.movietickets.common.dto.ApiResponse;
import com.shubh.movietickets.seats.dto.LockSeatsRequest;
import com.shubh.movietickets.seats.dto.LockSeatsResponse;
import com.shubh.movietickets.seats.dto.SeatDto;
import com.shubh.movietickets.seats.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<List<SeatDto>>> getSeats(
            @PathVariable UUID scheduleId) {
        List<SeatDto> seats = seatService.getSeatsForSchedule(scheduleId);
        return ResponseEntity.ok(ApiResponse.success(seats));
    }

    @GetMapping("/{scheduleId}/available")
    public ResponseEntity<ApiResponse<List<SeatDto>>> getAvailableSeats(
            @PathVariable UUID scheduleId) {
        List<SeatDto> seats = seatService.getAvailableSeats(scheduleId);
        return ResponseEntity.ok(ApiResponse.success(seats));
    }

    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<LockSeatsResponse>> lockSeats(
            @Valid @RequestBody LockSeatsRequest request) {
        LockSeatsResponse response = seatService.lockSeats(request);
        return ResponseEntity.ok(ApiResponse.success("Seats locked", response));
    }

    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<String>> unlockSeats(
            @RequestParam UUID scheduleId,
            @RequestParam UUID userId,
            @RequestParam List<String> seatNumbers) {
        seatService.unlockSeats(scheduleId, seatNumbers, userId);
        return ResponseEntity.ok(ApiResponse.success("Seats unlocked"));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Seats Service is running"));
    }
}
