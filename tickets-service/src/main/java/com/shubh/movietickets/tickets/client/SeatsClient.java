package com.shubh.movietickets.tickets.client;

import com.shubh.movietickets.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "seats-service")
public interface SeatsClient {

    @PostMapping("/api/seats/lock")
    ApiResponse<LockResponse> lockSeats(@RequestBody LockRequest request);

    @PostMapping("/api/seats/unlock")
    ApiResponse<String> unlockSeats(
            @RequestParam UUID scheduleId,
            @RequestParam UUID userId,
            @RequestParam List<String> seatNumbers);

    record LockRequest(UUID scheduleId, UUID userId, List<String> seatNumbers) {}
    record LockResponse(boolean locked, List<String> seatNumbers, BigDecimal totalAmount, int lockTtlSeconds) {}
}
