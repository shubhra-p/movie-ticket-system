package com.shubh.movietickets.tickets.service;

import com.shubh.movietickets.common.event.BookingConfirmedEvent;
import com.shubh.movietickets.common.event.BookingFailedEvent;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.tickets.client.PaymentClient;
import com.shubh.movietickets.tickets.client.SeatsClient;
import com.shubh.movietickets.tickets.dto.BookingDto;
import com.shubh.movietickets.tickets.dto.CreateBookingRequest;
import com.shubh.movietickets.tickets.entity.Booking;
import com.shubh.movietickets.tickets.entity.BookingStatus;
import com.shubh.movietickets.tickets.kafka.BookingEventProducer;
import com.shubh.movietickets.tickets.repository.BookingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 Saga orchestrator for the booking flow:

   Step 1: Lock seats (Feign → Seats Service)
   Step 2: Initiate payment (Feign → Payment Service)
   Step 3a: Payment success → publish BookingConfirmedEvent
   Step 3b: Payment failure → compensate (unlock seats) + publish BookingFailedEvent

 The Payment Service calls back asynchronously via webhook (handlePaymentCallback).
 A scheduled job (BookingExpiryScheduler) handles abandoned bookings.
 */
@Service
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatsClient seatsClient;
    private final PaymentClient paymentClient;
    private final BookingEventProducer eventProducer;

    /* Custom business metrics */
    private final Timer sagaDuration;
    private final Counter bookingsConfirmed;
    private final Counter bookingsFailed;
    private final Counter bookingsExpired;

    public BookingService(BookingRepository bookingRepository,
                          SeatsClient seatsClient,
                          PaymentClient paymentClient,
                          BookingEventProducer eventProducer,
                          MeterRegistry meterRegistry) {
        this.bookingRepository = bookingRepository;
        this.seatsClient = seatsClient;
        this.paymentClient = paymentClient;
        this.eventProducer = eventProducer;

        this.sagaDuration = Timer.builder("booking.saga.duration")
                .description("End-to-end booking saga duration (lock → payment initiation)")
                .register(meterRegistry);
        this.bookingsConfirmed = Counter.builder("booking.outcomes")
                .tag("result", "confirmed")
                .description("Bookings that completed successfully")
                .register(meterRegistry);
        this.bookingsFailed = Counter.builder("booking.outcomes")
                .tag("result", "failed")
                .description("Bookings that failed (payment declined, etc)")
                .register(meterRegistry);
        this.bookingsExpired = Counter.builder("booking.outcomes")
                .tag("result", "expired")
                .description("Bookings that expired due to lock timeout")
                .register(meterRegistry);
    }

    @Value("${booking.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${seats.lock.ttl-seconds:300}")
    private int lockTtlSeconds;

    /*
     Saga Step 1: Create booking and lock seats.
     Returns the booking in SEATS_LOCKED status with payment pending.
     */
    @Transactional
    @CircuitBreaker(name = "seats-service", fallbackMethod = "createBookingFallback")
    public BookingDto createBooking(CreateBookingRequest request) {
        log.info("Saga START: userId={}, scheduleId={}, seats={}",
                request.getUserId(), request.getScheduleId(), request.getSeatNumbers());

        /* Step 1: Lock seats via Seats Service */
        var lockResponse = seatsClient.lockSeats(
                new SeatsClient.LockRequest(request.getScheduleId(), request.getUserId(), request.getSeatNumbers()));

        var lockData = lockResponse.getData();
        Instant lockExpiry = Instant.now().plusSeconds(lockData.lockTtlSeconds());

        /* Persist booking */
        Booking booking = Booking.builder()
                .userId(request.getUserId())
                .scheduleId(request.getScheduleId())
                .seatNumbers(request.getSeatNumbers())
                .totalAmount(lockData.totalAmount())
                .status(BookingStatus.SEATS_LOCKED)
                .lockExpiresAt(lockExpiry)
                .build();

        booking = bookingRepository.save(booking);

        log.info("Saga Step 1 COMPLETE: bookingId={}, seats locked, amount={}, expiresAt={}",
                booking.getId(), lockData.totalAmount(), lockExpiry);

        return toDto(booking);
    }

    /*
     Saga Step 2: Initiate payment for a locked booking.
     Payment Service processes asynchronously and calls back via webhook.
     */
    @Transactional
    @CircuitBreaker(name = "payment-service", fallbackMethod = "initiatePaymentFallback")
    public BookingDto initiatePayment(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (booking.getStatus() != BookingStatus.SEATS_LOCKED) {
            throw new IllegalStateException("Booking is not in SEATS_LOCKED state: " + booking.getStatus());
        }

        /* Check if lock has expired */
        if (Instant.now().isAfter(booking.getLockExpiresAt())) {
            throw new IllegalStateException("Seat lock has expired for booking: " + bookingId);
        }

        String idempotencyKey = "booking-" + bookingId;
        String callbackUrl = callbackBaseUrl + "/api/bookings/callback/payment";

        var paymentResponse = paymentClient.initiatePayment(
                new PaymentClient.PaymentRequest(
                        idempotencyKey,
                        bookingId,
                        booking.getUserId(),
                        booking.getTotalAmount(),
                        "INR",
                        callbackUrl));

        booking.setPaymentId(paymentResponse.getData().paymentId());
        booking.setStatus(BookingStatus.PAYMENT_PENDING);
        bookingRepository.save(booking);

        log.info("Saga Step 2 COMPLETE: bookingId={}, paymentId={}, status=PAYMENT_PENDING",
                bookingId, paymentResponse.getData().paymentId());

        return toDto(booking);
    }

    /*
     Saga Step 3: Handle the async payment callback from Payment Service.
     This is the webhook endpoint that the Payment Service POSTs to.
     */
    @Transactional
    public void handlePaymentCallback(UUID bookingId, String paymentStatus, String failureReason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (booking.getStatus() != BookingStatus.PAYMENT_PENDING) {
            log.warn("Ignoring callback for booking {} in status {}", bookingId, booking.getStatus());
            return;
        }

        if ("SUCCESS".equals(paymentStatus)) {
            /* Saga COMPLETE: confirm booking */
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            eventProducer.publishBookingConfirmed(BookingConfirmedEvent.builder()
                    .sourceService("tickets-service")
                    .bookingId(bookingId)
                    .scheduleId(booking.getScheduleId())
                    .userId(booking.getUserId())
                    .seatNumbers(booking.getSeatNumbers())
                    .totalAmount(booking.getTotalAmount())
                    .build());

            log.info("Saga COMPLETE: bookingId={}, status=CONFIRMED", bookingId);

        } else {
            /* Saga COMPENSATE: payment failed */
            compensateBooking(booking, BookingStatus.FAILED,
                    "Payment " + paymentStatus + ": " + failureReason);
        }
    }

    /*
     Compensation: unlock seats and set booking to the given terminal status.
     The targetStatus allows callers to distinguish between FAILED (payment failed)
     and EXPIRED (lock timed out) — producing a consistent DB state and Kafka event.
     */
    @Transactional
    public void compensateBooking(Booking booking, BookingStatus targetStatus, String reason) {
        booking.setStatus(targetStatus);
        booking.setFailureReason(reason);
        bookingRepository.save(booking);

        /* Compensating action: release seats via Feign (best-effort) */
        try {
            seatsClient.unlockSeats(booking.getScheduleId(), booking.getUserId(), booking.getSeatNumbers());
        } catch (Exception e) {
            log.error("Failed to unlock seats for bookingId={}: {}", booking.getId(), e.getMessage());
        }

        /* Publish failure event so Seats Service can also clean up via Kafka */
        eventProducer.publishBookingFailed(BookingFailedEvent.builder()
                .sourceService("tickets-service")
                .bookingId(booking.getId())
                .scheduleId(booking.getScheduleId())
                .userId(booking.getUserId())
                .seatNumbers(booking.getSeatNumbers())
                .reason(reason)
                .build());

        log.info("Saga COMPENSATED: bookingId={}, status={}, reason={}",
                booking.getId(), targetStatus, reason);
    }

    /*
     Expire bookings that have exceeded their lock TTL without completing payment.
     Called by BookingExpiryScheduler.
     Uses a single query with IN clause instead of two separate queries.
     */
    @Transactional
    public int expireStaleBookings() {
        List<Booking> stale = bookingRepository.findStaleBookings(
                List.of(BookingStatus.SEATS_LOCKED, BookingStatus.PAYMENT_PENDING),
                Instant.now());

        for (Booking booking : stale) {
            compensateBooking(booking, BookingStatus.EXPIRED,
                    "Lock expired after " + lockTtlSeconds + " seconds");
        }

        if (!stale.isEmpty()) {
            log.info("Expired {} stale bookings", stale.size());
        }
        return stale.size();
    }

    public BookingDto getBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        return toDto(booking);
    }

    public List<BookingDto> getBookingsForUser(UUID userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::toDto).toList();
    }

    private BookingDto createBookingFallback(CreateBookingRequest req, Throwable t) {
        log.error("Seats Service unavailable: {}", t.getMessage());
        throw new RuntimeException("Seats Service is unavailable. Please try again later.");
    }

    private BookingDto initiatePaymentFallback(UUID bookingId, Throwable t) {
        log.error("Payment Service unavailable: {}", t.getMessage());
        throw new RuntimeException("Payment Service is unavailable. Please try again later.");
    }

    private BookingDto toDto(Booking booking) {
        return BookingDto.builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .scheduleId(booking.getScheduleId())
                .seatNumbers(booking.getSeatNumbers())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .paymentId(booking.getPaymentId())
                .failureReason(booking.getFailureReason())
                .lockExpiresAt(booking.getLockExpiresAt())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
