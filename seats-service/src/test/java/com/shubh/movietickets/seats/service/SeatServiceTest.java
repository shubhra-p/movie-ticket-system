package com.shubh.movietickets.seats.service;

import com.shubh.movietickets.common.exception.ConflictException;
import com.shubh.movietickets.seats.dto.LockSeatsRequest;
import com.shubh.movietickets.seats.dto.LockSeatsResponse;
import com.shubh.movietickets.seats.entity.Seat;
import com.shubh.movietickets.seats.entity.SeatStatus;
import com.shubh.movietickets.seats.repository.SeatRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RedisLockService redisLockService;

    private SeatService seatService;

    private UUID scheduleId;
    private UUID userId;
    private List<String> seatNumbers;

    @BeforeEach
    void setUp() {
        seatService = new SeatService(seatRepository, redisLockService, new SimpleMeterRegistry());
        scheduleId = UUID.randomUUID();
        userId = UUID.randomUUID();
        seatNumbers = List.of("A1", "A2");
    }


    @Test
    @DisplayName("Lock seats - success (Redis + Postgres both succeed)")
    void lockSeats_success() {
        LockSeatsRequest request = new LockSeatsRequest(scheduleId, userId, seatNumbers);

        when(redisLockService.lockSeats(scheduleId, seatNumbers, userId)).thenReturn(true);
        when(seatRepository.bulkUpdateStatus(scheduleId, seatNumbers,
                SeatStatus.AVAILABLE, SeatStatus.LOCKED, userId)).thenReturn(2);
        when(redisLockService.getLockTtlSeconds()).thenReturn(300);

        List<Seat> lockedSeats = List.of(
                Seat.builder().seatNumber("A1").price(BigDecimal.valueOf(200)).build(),
                Seat.builder().seatNumber("A2").price(BigDecimal.valueOf(200)).build()
        );
        when(seatRepository.findByScheduleIdAndSeatNumberIn(scheduleId, seatNumbers))
                .thenReturn(lockedSeats);

        LockSeatsResponse response = seatService.lockSeats(request);

        assertThat(response.isLocked()).isTrue();
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));
        assertThat(response.getLockTtlSeconds()).isEqualTo(300);
    }

    @Test
    @DisplayName("Lock seats - Redis lock fails (another user has them)")
    void lockSeats_redisConflict() {
        LockSeatsRequest request = new LockSeatsRequest(scheduleId, userId, seatNumbers);

        when(redisLockService.lockSeats(scheduleId, seatNumbers, userId)).thenReturn(false);

        assertThatThrownBy(() -> seatService.lockSeats(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already locked");

        // Postgres should never be hit
        verify(seatRepository, never()).bulkUpdateStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lock seats - Redis succeeds but Postgres conflict (stale data) → Redis rollback")
    void lockSeats_postgresConflict_rollsBackRedis() {
        LockSeatsRequest request = new LockSeatsRequest(scheduleId, userId, seatNumbers);

        when(redisLockService.lockSeats(scheduleId, seatNumbers, userId)).thenReturn(true);
        // Only 1 of 2 seats was actually AVAILABLE in Postgres
        when(seatRepository.bulkUpdateStatus(scheduleId, seatNumbers,
                SeatStatus.AVAILABLE, SeatStatus.LOCKED, userId)).thenReturn(1);

        assertThatThrownBy(() -> seatService.lockSeats(request))
                .isInstanceOf(ConflictException.class);

        // Redis locks should be rolled back
        verify(redisLockService).unlockSeats(scheduleId, seatNumbers, userId);
    }

    @Test
    @DisplayName("Confirm seats - flips LOCKED → BOOKED and deletes Redis locks")
    void confirmSeats() {
        when(seatRepository.bulkUpdateStatus(scheduleId, seatNumbers,
                SeatStatus.LOCKED, SeatStatus.BOOKED, userId)).thenReturn(2);

        seatService.confirmSeats(scheduleId, seatNumbers, userId);

        verify(seatRepository).bulkUpdateStatus(scheduleId, seatNumbers,
                SeatStatus.LOCKED, SeatStatus.BOOKED, userId);
        verify(redisLockService).deleteLocksForSeats(scheduleId, seatNumbers);
    }

    @Test
    @DisplayName("Release seats - flips LOCKED → AVAILABLE and deletes Redis locks")
    void releaseSeats() {
        seatService.releaseSeats(scheduleId, seatNumbers, userId);

        verify(seatRepository).bulkUpdateStatus(scheduleId, seatNumbers,
                SeatStatus.LOCKED, SeatStatus.AVAILABLE, null);
        verify(redisLockService).deleteLocksForSeats(scheduleId, seatNumbers);
    }

    @Test
    @DisplayName("Seed seats - generates correct seat numbers from layout")
    void seedSeats() {
        List<SeatService.SeatSeedInfo> layouts = List.of(
                new SeatService.SeatSeedInfo("REGULAR", "A", 2, 3, BigDecimal.valueOf(200))
        );

        when(seatRepository.existsByScheduleId(scheduleId)).thenReturn(false);

        seatService.seedSeats(scheduleId, layouts);

        verify(seatRepository).saveAll(argThat((List<Seat> seats) -> {
            assertThat(seats).hasSize(6); // 2 rows x 3 seats
            assertThat(seats.stream().map(Seat::getSeatNumber).toList())
                    .containsExactlyInAnyOrder("A1", "A2", "A3", "B1", "B2", "B3");
            return true;
        }));
    }

    @Test
    @DisplayName("Seed seats - idempotent (skips if already seeded)")
    void seedSeats_idempotent() {
        when(seatRepository.existsByScheduleId(scheduleId)).thenReturn(true);

        seatService.seedSeats(scheduleId, List.of());

        verify(seatRepository, never()).saveAll(any());
    }
}
