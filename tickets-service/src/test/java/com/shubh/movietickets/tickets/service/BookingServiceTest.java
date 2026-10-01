package com.shubh.movietickets.tickets.service;

import com.shubh.movietickets.common.dto.ApiResponse;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.tickets.client.PaymentClient;
import com.shubh.movietickets.tickets.client.SeatsClient;
import com.shubh.movietickets.tickets.dto.BookingDto;
import com.shubh.movietickets.tickets.dto.CreateBookingRequest;
import com.shubh.movietickets.tickets.entity.Booking;
import com.shubh.movietickets.tickets.entity.BookingStatus;
import com.shubh.movietickets.tickets.kafka.BookingEventProducer;
import com.shubh.movietickets.tickets.repository.BookingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private SeatsClient seatsClient;
    @Mock private PaymentClient paymentClient;
    @Mock private BookingEventProducer eventProducer;

    private BookingService bookingService;

    private UUID userId;
    private UUID scheduleId;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, seatsClient, paymentClient, eventProducer, new SimpleMeterRegistry());
        userId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        ReflectionTestUtils.setField(bookingService, "callbackBaseUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(bookingService, "lockTtlSeconds", 300);
    }

    @Test
    @DisplayName("Saga Step 1: Create booking locks seats successfully")
    void createBooking_success() {
        var lockResponse = new SeatsClient.LockResponse(true, List.of("A1", "A2"), BigDecimal.valueOf(400), 300);
        when(seatsClient.lockSeats(any())).thenReturn(ApiResponse.success("Locked", lockResponse));

        Booking savedBooking = Booking.builder()
                .id(bookingId).userId(userId).scheduleId(scheduleId)
                .seatNumbers(List.of("A1", "A2")).totalAmount(BigDecimal.valueOf(400))
                .status(BookingStatus.SEATS_LOCKED).lockExpiresAt(Instant.now().plusSeconds(300))
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        CreateBookingRequest request = new CreateBookingRequest(userId, scheduleId, List.of("A1", "A2"));
        BookingDto result = bookingService.createBooking(request);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.SEATS_LOCKED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));
        verify(seatsClient).lockSeats(any());
    }

    @Test
    @DisplayName("Saga Step 2: Initiate payment transitions to PAYMENT_PENDING")
    void initiatePayment_success() {
        Booking booking = Booking.builder()
                .id(bookingId).userId(userId).scheduleId(scheduleId)
                .seatNumbers(List.of("A1")).totalAmount(BigDecimal.valueOf(200))
                .status(BookingStatus.SEATS_LOCKED).lockExpiresAt(Instant.now().plusSeconds(300))
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        var paymentResp = new PaymentClient.PaymentResponse(UUID.randomUUID(), bookingId, "PENDING", BigDecimal.valueOf(200), null);
        when(paymentClient.initiatePayment(any())).thenReturn(ApiResponse.success("Initiated", paymentResp));
        when(bookingRepository.save(any())).thenReturn(booking);

        BookingDto result = bookingService.initiatePayment(bookingId);

        assertThat(result.getStatus()).isEqualTo(BookingStatus.PAYMENT_PENDING);
        verify(paymentClient).initiatePayment(any());
    }

    @Test
    @DisplayName("Saga Step 3a: Payment callback SUCCESS confirms booking")
    void handlePaymentCallback_success() {
        Booking booking = Booking.builder()
                .id(bookingId).userId(userId).scheduleId(scheduleId)
                .seatNumbers(List.of("A1")).totalAmount(BigDecimal.valueOf(200))
                .status(BookingStatus.PAYMENT_PENDING)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);

        bookingService.handlePaymentCallback(bookingId, "SUCCESS", null);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(eventProducer).publishBookingConfirmed(any());
    }

    @Test
    @DisplayName("Saga Step 3b: Payment callback FAILED triggers compensation")
    void handlePaymentCallback_failed() {
        Booking booking = Booking.builder()
                .id(bookingId).userId(userId).scheduleId(scheduleId)
                .seatNumbers(List.of("A1")).totalAmount(BigDecimal.valueOf(200))
                .status(BookingStatus.PAYMENT_PENDING)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenReturn(booking);

        bookingService.handlePaymentCallback(bookingId, "FAILED", "Insufficient funds");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(seatsClient).unlockSeats(any(), any(), any());
        verify(eventProducer).publishBookingFailed(any());
    }

    @Test
    @DisplayName("Initiate payment fails if lock expired")
    void initiatePayment_lockExpired() {
        Booking booking = Booking.builder()
                .id(bookingId).userId(userId).scheduleId(scheduleId)
                .status(BookingStatus.SEATS_LOCKED)
                .lockExpiresAt(Instant.now().minusSeconds(60))
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.initiatePayment(bookingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Get booking not found throws exception")
    void getBooking_notFound() {
        when(bookingRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> bookingService.getBooking(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
