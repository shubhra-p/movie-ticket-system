package com.shubh.movietickets.seats.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shubh.movietickets.common.exception.ConflictException;
import com.shubh.movietickets.common.exception.GlobalExceptionHandler;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.seats.dto.LockSeatsRequest;
import com.shubh.movietickets.seats.dto.LockSeatsResponse;
import com.shubh.movietickets.seats.dto.SeatDto;
import com.shubh.movietickets.seats.entity.SeatStatus;
import com.shubh.movietickets.seats.service.SeatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SeatController.class)
@Import(GlobalExceptionHandler.class)
class SeatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SeatService seatService;

    @Test
    @DisplayName("GET /api/seats/{scheduleId} — returns all seats")
    void getSeats_success() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        List<SeatDto> seats = List.of(
                SeatDto.builder().id(UUID.randomUUID()).seatNumber("A1")
                        .category("REGULAR").price(BigDecimal.valueOf(200))
                        .status(SeatStatus.AVAILABLE).build(),
                SeatDto.builder().id(UUID.randomUUID()).seatNumber("A2")
                        .category("REGULAR").price(BigDecimal.valueOf(200))
                        .status(SeatStatus.LOCKED).build()
        );
        when(seatService.getSeatsForSchedule(scheduleId)).thenReturn(seats);

        mockMvc.perform(get("/api/seats/{scheduleId}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].seatNumber").value("A1"))
                .andExpect(jsonPath("$.data[1].status").value("LOCKED"));
    }

    @Test
    @DisplayName("GET /api/seats/{scheduleId} — 404 when schedule not found")
    void getSeats_notFound() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(seatService.getSeatsForSchedule(scheduleId))
                .thenThrow(new ResourceNotFoundException("Seats", "scheduleId", scheduleId));

        mockMvc.perform(get("/api/seats/{scheduleId}", scheduleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/seats/{scheduleId}/available — returns only AVAILABLE seats")
    void getAvailableSeats_success() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        List<SeatDto> seats = List.of(
                SeatDto.builder().id(UUID.randomUUID()).seatNumber("A1")
                        .category("REGULAR").price(BigDecimal.valueOf(200))
                        .status(SeatStatus.AVAILABLE).build()
        );
        when(seatService.getAvailableSeats(scheduleId)).thenReturn(seats);

        mockMvc.perform(get("/api/seats/{scheduleId}/available", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/seats/lock — 200 on successful lock")
    void lockSeats_success() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LockSeatsRequest request = new LockSeatsRequest(scheduleId, userId, List.of("A1", "A2"));

        LockSeatsResponse response = LockSeatsResponse.builder()
                .locked(true)
                .seatNumbers(List.of("A1", "A2"))
                .totalAmount(BigDecimal.valueOf(400))
                .lockTtlSeconds(300)
                .build();
        when(seatService.lockSeats(any(LockSeatsRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/seats/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.locked").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(400))
                .andExpect(jsonPath("$.data.lockTtlSeconds").value(300));
    }

    @Test
    @DisplayName("POST /api/seats/lock — 409 on contention")
    void lockSeats_conflict() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LockSeatsRequest request = new LockSeatsRequest(scheduleId, userId, List.of("A1", "A2"));

        when(seatService.lockSeats(any(LockSeatsRequest.class)))
                .thenThrow(new ConflictException("One or more seats are already locked"));

        mockMvc.perform(post("/api/seats/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("One or more seats are already locked"));
    }

    @Test
    @DisplayName("POST /api/seats/lock — 400 on missing required fields")
    void lockSeats_validationError() throws Exception {
        // Empty request (missing all required fields)
        String emptyBody = "{}";

        mockMvc.perform(post("/api/seats/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/seats/unlock — 200 on successful unlock")
    void unlockSeats_success() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        doNothing().when(seatService).unlockSeats(scheduleId, List.of("A1", "A2"), userId);

        mockMvc.perform(post("/api/seats/unlock")
                        .param("scheduleId", scheduleId.toString())
                        .param("userId", userId.toString())
                        .param("seatNumbers", "A1", "A2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(seatService).unlockSeats(scheduleId, List.of("A1", "A2"), userId);
    }

    @Test
    @DisplayName("GET /api/seats/health — returns 200")
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/seats/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
