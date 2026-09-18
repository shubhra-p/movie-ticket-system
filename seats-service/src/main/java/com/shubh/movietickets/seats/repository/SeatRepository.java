package com.shubh.movietickets.seats.repository;

import com.shubh.movietickets.seats.entity.Seat;
import com.shubh.movietickets.seats.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByScheduleId(UUID scheduleId);

    List<Seat> findByScheduleIdAndStatus(UUID scheduleId, SeatStatus status);

    Optional<Seat> findByScheduleIdAndSeatNumber(UUID scheduleId, String seatNumber);

    List<Seat> findByScheduleIdAndSeatNumberIn(UUID scheduleId, List<String> seatNumbers);

    @Modifying
    @Query("UPDATE Seat s SET s.status = :status, s.lockedByUserId = :userId, s.version = s.version + 1 " +
           "WHERE s.scheduleId = :scheduleId AND s.seatNumber IN :seatNumbers AND s.status = :expectedStatus")
    int bulkUpdateStatus(@Param("scheduleId") UUID scheduleId,
                         @Param("seatNumbers") List<String> seatNumbers,
                         @Param("expectedStatus") SeatStatus expectedStatus,
                         @Param("status") SeatStatus newStatus,
                         @Param("userId") UUID userId);

    boolean existsByScheduleId(UUID scheduleId);

    long countByScheduleIdAndStatus(UUID scheduleId, SeatStatus status);
}
