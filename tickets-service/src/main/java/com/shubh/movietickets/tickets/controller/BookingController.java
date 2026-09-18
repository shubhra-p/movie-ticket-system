package com.shubh.movietickets.tickets.controller;

import com.shubh.movietickets.common.dto.ApiResponse;
import com.shubh.movietickets.tickets.dto.BookingDto;
import com.shubh.movietickets.tickets.dto.CreateBookingRequest;
import com.shubh.movietickets.tickets.dto.PaymentCallbackRequest;
import com.shubh.movietickets.tickets.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /* Saga Step 1: Create booking + lock seats */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingDto>> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {
        BookingDto booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking created, seats locked", booking));
    }

    /* Saga Step 2: Initiate payment */
    @PostMapping("/{bookingId}/pay")
    public ResponseEntity<ApiResponse<BookingDto>> initiatePayment(@PathVariable UUID bookingId) {
        BookingDto booking = bookingService.initiatePayment(bookingId);
        return ResponseEntity.ok(ApiResponse.success("Payment initiated", booking));
    }

    /* Webhook: Payment Service calls this with the payment result */
    @PostMapping("/callback/payment")
    public ResponseEntity<Void> paymentCallback(@RequestBody PaymentCallbackRequest callback) {
        bookingService.handlePaymentCallback(
                callback.getBookingId(),
                callback.getStatus(),
                callback.getFailureReason());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<BookingDto>> getBooking(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBooking(bookingId)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getUserBookings(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingsForUser(userId)));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Tickets Service is running"));
    }
}
