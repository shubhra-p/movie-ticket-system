package com.shubh.movietickets.tickets.scheduler;

import com.shubh.movietickets.tickets.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 Runs every 30 seconds to expire bookings whose seat locks have timed out.
 This ensures abandoned checkout flows don't permanently lock seats.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpiryScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedRate = 30000)
    public void expireStaleBookings() {
        int expired = bookingService.expireStaleBookings();
        if (expired > 0) {
            log.info("Booking expiry sweep: {} bookings expired", expired);
        }
    }
}
