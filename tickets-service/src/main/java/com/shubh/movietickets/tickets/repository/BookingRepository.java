package com.shubh.movietickets.tickets.repository;

import com.shubh.movietickets.tickets.entity.Booking;
import com.shubh.movietickets.tickets.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUserId(UUID userId);
    List<Booking> findByScheduleId(UUID scheduleId);
    List<Booking> findByStatusAndLockExpiresAtBefore(BookingStatus status, Instant now);

    /* Single query to find bookings in any of the given statuses with expired locks */
    @Query("SELECT b FROM Booking b WHERE b.status IN :statuses AND b.lockExpiresAt < :now")
    List<Booking> findStaleBookings(@Param("statuses") List<BookingStatus> statuses,
                                    @Param("now") Instant now);
}

