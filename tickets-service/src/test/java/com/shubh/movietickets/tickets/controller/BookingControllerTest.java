package com.shubh.movietickets.tickets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shubh.movietickets.common.exception.ConflictException;
import com.shubh.movietickets.common.exception.GlobalExceptionHandler;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.tickets.dto.BookingDto;
import com.shubh.movietickets.tickets.dto.CreateBookingRequest;
import com.shubh.movietickets.tickets.dto.PaymentCallbackRequest;
import com.shubh.movietickets.tickets.entity.BookingStatus;
import com.shubh.movietickets.tickets.service.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
@Import(GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    private BookingDto sampleBookingDto() {
        return BookingDto.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .scheduleId(UUID.randomUUID())
                .seatNumbers(List.of("A1", "A2"))
                .totalAmount(BigDecimal.valueOf(400))
                .status(BookingStatus.SEATS_LOCKED)
                .lockExpiresAt(Instant.now().plusSeconds(300))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/bookings — 201 on successful booking creation")
    void createBooking_success() throws Exception {
        BookingDto dto = sampleBookingDto();
        when(bookingService.createBooking(any(CreateBookingRequest.class))).thenReturn(dto);

        CreateBookingRequest request = new CreateBookingRequest(
                dto.getUserId(), dto.getScheduleId(), List.of("A1", "A2"));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Booking created, seats locked"))
                .andExpect(jsonPath("$.data.status").value("SEATS_LOCKED"))
                .andExpect(jsonPath("$.data.totalAmount").value(400));
    }

    @Test
    @DisplayName("POST /api/bookings — 409 when seats are already locked")
    void createBooking_conflict() throws Exception {
        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenThrow(new ConflictException("Seats already locked by another user"));

        CreateBookingRequest request = new CreateBookingRequest(
                UUID.randomUUID(), UUID.randomUUID(), List.of("A1"));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Seats already locked by another user"));
    }

    @Test
    @DisplayName("POST /api/bookings — 400 on validation error (missing userId)")
    void createBooking_validationError() throws Exception {
        String invalidBody = """
                {
                    "scheduleId": "%s",
                    "seatNumbers": ["A1"]
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/bookings/{bookingId}/pay — 200 on successful payment initiation")
    void initiatePayment_success() throws Exception {
        BookingDto dto = sampleBookingDto();
        dto.setStatus(BookingStatus.PAYMENT_PENDING);
        dto.setPaymentId(UUID.randomUUID());
        when(bookingService.initiatePayment(dto.getId())).thenReturn(dto);

        mockMvc.perform(post("/api/bookings/{bookingId}/pay", dto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"));
    }

    @Test
    @DisplayName("POST /api/bookings/{bookingId}/pay — 409 when lock expired")
    void initiatePayment_lockExpired() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.initiatePayment(bookingId))
                .thenThrow(new IllegalStateException("Seat lock has expired"));

        mockMvc.perform(post("/api/bookings/{bookingId}/pay", bookingId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/bookings/{bookingId}/pay — 404 when booking not found")
    void initiatePayment_notFound() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.initiatePayment(bookingId))
                .thenThrow(new ResourceNotFoundException("Booking", "id", bookingId));

        mockMvc.perform(post("/api/bookings/{bookingId}/pay", bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/bookings/callback/payment — 200 on valid callback")
    void paymentCallback_success() throws Exception {
        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                UUID.randomUUID(), UUID.randomUUID(), "SUCCESS",
                BigDecimal.valueOf(400), null);

        doNothing().when(bookingService).handlePaymentCallback(
                callback.getBookingId(), callback.getStatus(), callback.getFailureReason());

        mockMvc.perform(post("/api/bookings/callback/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk());

        verify(bookingService).handlePaymentCallback(
                callback.getBookingId(), "SUCCESS", null);
    }

    @Test
    @DisplayName("POST /api/bookings/callback/payment — failure callback")
    void paymentCallback_failure() throws Exception {
        PaymentCallbackRequest callback = new PaymentCallbackRequest(
                UUID.randomUUID(), UUID.randomUUID(), "FAILED",
                BigDecimal.valueOf(400), "Insufficient funds");

        mockMvc.perform(post("/api/bookings/callback/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk());

        verify(bookingService).handlePaymentCallback(
                callback.getBookingId(), "FAILED", "Insufficient funds");
    }

    @Test
    @DisplayName("GET /api/bookings/{bookingId} — 200 returns booking")
    void getBooking_success() throws Exception {
        BookingDto dto = sampleBookingDto();
        when(bookingService.getBooking(dto.getId())).thenReturn(dto);

        mockMvc.perform(get("/api/bookings/{bookingId}", dto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.seatNumbers").isArray());
    }

    @Test
    @DisplayName("GET /api/bookings/{bookingId} — 404 when not found")
    void getBooking_notFound() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.getBooking(bookingId))
                .thenThrow(new ResourceNotFoundException("Booking", "id", bookingId));

        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/bookings/user/{userId} — 200 returns user bookings")
    void getUserBookings_success() throws Exception {
        UUID userId = UUID.randomUUID();
        BookingDto dto = sampleBookingDto();
        dto.setUserId(userId);
        when(bookingService.getBookingsForUser(userId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/bookings/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/bookings/health — returns 200")
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/bookings/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
