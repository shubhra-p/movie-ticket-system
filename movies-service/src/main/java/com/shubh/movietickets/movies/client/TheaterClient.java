package com.shubh.movietickets.movies.client;

import com.shubh.movietickets.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/*
 Feign client for Theaters Service.
 Used to validate screenId and fetch screen/theater metadata
 when creating a movie schedule.
 */
@FeignClient(name = "theaters-service", url = "${feign.theaters-service.url}")
public interface TheaterClient {

    @GetMapping("/api/theaters/screens/{screenId}")
    ApiResponse<ScreenInfo> getScreen(@PathVariable UUID screenId);

    @GetMapping("/api/theaters/{theaterId}")
    ApiResponse<TheaterInfo> getTheater(@PathVariable UUID theaterId);

    record ScreenInfo(UUID id, String name, int totalSeats, java.util.List<SeatLayoutInfo> seatLayouts) {}
    record TheaterInfo(UUID id, String name, String city, String address, java.util.List<ScreenInfo> screens) {}
    record SeatLayoutInfo(UUID id, String category, String rowPrefix, int rowCount, int seatsPerRow, java.math.BigDecimal price) {}
}
