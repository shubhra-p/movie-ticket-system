package com.shubh.movietickets.seats.service;

import com.shubh.movietickets.common.exception.ConflictException;
import com.shubh.movietickets.common.exception.ResourceNotFoundException;
import com.shubh.movietickets.seats.dto.LockSeatsRequest;
import com.shubh.movietickets.seats.dto.LockSeatsResponse;
import com.shubh.movietickets.seats.dto.SeatDto;
import com.shubh.movietickets.seats.entity.Seat;
import com.shubh.movietickets.seats.entity.SeatStatus;
import com.shubh.movietickets.seats.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final RedisLockService redisLockService;

    /* Custom business metrics */
    private final Counter lockAttempts;
    private final Counter lockFailures;
    private final Counter lockSuccesses;
    private final Timer lockDuration;

    public SeatService(SeatRepository seatRepository,
                       RedisLockService redisLockService,
                       MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.redisLockService = redisLockService;

        this.lockAttempts = Counter.builder("seat.lock.attempts")
                .description("Total seat lock attempts")
                .register(meterRegistry);
        this.lockFailures = Counter.builder("seat.lock.failures")
                .description("Seat lock attempts that failed (contention)")
                .register(meterRegistry);
        this.lockSuccesses = Counter.builder("seat.lock.successes")
                .description("Seat lock attempts that succeeded")
                .register(meterRegistry);
        this.lockDuration = Timer.builder("seat.lock.duration")
                .description("Time to acquire seat lock (dual-layer)")
                .register(meterRegistry);
    }

    /*
     Lock seats for a user, dual-layer concurrency control:
       Layer 1: Redis SET NX EX (fast, distributed)
       Layer 2: Postgres UPDATE WHERE status=AVAILABLE (durable, optimistic)

     If Redis lock fails → instant rejection (no DB hit)
     If Redis succeeds but Postgres fails → rollback Redis lock
     Both must succeed for the lock to be confirmed.
     */
    @Transactional
    public LockSeatsResponse lockSeats(LockSeatsRequest request) {
        UUID scheduleId = request.getScheduleId();
        UUID userId = request.getUserId();
        List<String> seatNumbers = request.getSeatNumbers();

        lockAttempts.increment();
        log.info("Lock attempt: scheduleId={}, userId={}, seats={}", scheduleId, userId, seatNumbers);

        return lockDuration.record(() -> {
            // Layer 1: Redis distributed lock (fast path)
            boolean redisLocked = redisLockService.lockSeats(scheduleId, seatNumbers, userId);
            if (!redisLocked) {
                lockFailures.increment();
                throw new ConflictException("One or more seats are already locked by another user");
            }

            try {
                // Layer 2: Postgres optimistic update (durable path)
                int updated = seatRepository.bulkUpdateStatus(
                        scheduleId, seatNumbers,
                        SeatStatus.AVAILABLE, SeatStatus.LOCKED, userId);

                if (updated != seatNumbers.size()) {
                    lockFailures.increment();
                    // Some seats were not AVAILABLE in Postgres — rollback Redis
                    throw new ConflictException(
                            "Seat conflict: expected " + seatNumbers.size() +
                            " available seats, but only " + updated + " were lockable");
                }

                // Calculate total amount
                List<Seat> lockedSeats = seatRepository.findByScheduleIdAndSeatNumberIn(scheduleId, seatNumbers);
                BigDecimal totalAmount = lockedSeats.stream()
                        .map(Seat::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                lockSuccesses.increment();
                log.info("Lock SUCCESS: scheduleId={}, userId={}, seats={}, total={}",
                        scheduleId, userId, seatNumbers, totalAmount);

                return LockSeatsResponse.builder()
                        .locked(true)
                        .seatNumbers(seatNumbers)
                        .totalAmount(totalAmount)
                        .lockTtlSeconds(redisLockService.getLockTtlSeconds())
                        .build();

            } catch (Exception e) {
                // Rollback Redis locks if Postgres update failed
                redisLockService.unlockSeats(scheduleId, seatNumbers, userId);
                throw e;
            }
        });
    }

    /*
     Release seats, unlock in both Redis and Postgres.
     Only the user who locked them can release.
     */
    @Transactional
    public void unlockSeats(UUID scheduleId, List<String> seatNumbers, UUID userId) {
        redisLockService.unlockSeats(scheduleId, seatNumbers, userId);
        seatRepository.bulkUpdateStatus(
                scheduleId, seatNumbers,
                SeatStatus.LOCKED, SeatStatus.AVAILABLE, null);

        log.info("Unlock: scheduleId={}, userId={}, seats={}", scheduleId, userId, seatNumbers);
    }

    /*
     Confirm booking, flip LOCKED → BOOKED and delete Redis locks.
     Called by Kafka consumer when BookingConfirmedEvent arrives.
     */
    @Transactional
    public void confirmSeats(UUID scheduleId, List<String> seatNumbers, UUID userId) {
        int updated = seatRepository.bulkUpdateStatus(
                scheduleId, seatNumbers,
                SeatStatus.LOCKED, SeatStatus.BOOKED, userId);

        redisLockService.deleteLocksForSeats(scheduleId, seatNumbers);

        log.info("Confirm booking: scheduleId={}, userId={}, seats={}, updated={}",
                scheduleId, userId, seatNumbers, updated);
    }

    /*
     Release seats after booking failure, flip LOCKED → AVAILABLE and delete Redis locks.
     Called by Kafka consumer when BookingFailedEvent arrives.
     */
    @Transactional
    public void releaseSeats(UUID scheduleId, List<String> seatNumbers, UUID userId) {
        seatRepository.bulkUpdateStatus(
                scheduleId, seatNumbers,
                SeatStatus.LOCKED, SeatStatus.AVAILABLE, null);

        redisLockService.deleteLocksForSeats(scheduleId, seatNumbers);

        log.info("Release (booking failed): scheduleId={}, userId={}, seats={}",
                scheduleId, userId, seatNumbers);
    }

    public List<SeatDto> getSeatsForSchedule(UUID scheduleId) {
        if (!seatRepository.existsByScheduleId(scheduleId)) {
            throw new ResourceNotFoundException("Seats", "scheduleId", scheduleId);
        }
        return seatRepository.findByScheduleId(scheduleId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<SeatDto> getAvailableSeats(UUID scheduleId) {
        return seatRepository.findByScheduleIdAndStatus(scheduleId, SeatStatus.AVAILABLE).stream()
                .map(this::toDto)
                .toList();
    }

    /*
     Seed seats from a ScheduleCreatedEvent.
     Generates individual seat records from the seat layout configuration.
     Idempotent: skips if seats already exist for the schedule.
     */
    @Transactional
    public void seedSeats(UUID scheduleId, List<SeatSeedInfo> layouts) {
        if (seatRepository.existsByScheduleId(scheduleId)) {
            log.warn("Seats already seeded for scheduleId={}, skipping", scheduleId);
            return;
        }

        List<Seat> seats = new java.util.ArrayList<>();
        for (SeatSeedInfo layout : layouts) {
            char startRow = layout.rowPrefix().charAt(0);
            for (int row = 0; row < layout.rowCount(); row++) {
                char rowChar = (char) (startRow + row);
                for (int col = 1; col <= layout.seatsPerRow(); col++) {
                    seats.add(Seat.builder()
                            .scheduleId(scheduleId)
                            .seatNumber(rowChar + String.valueOf(col))
                            .category(layout.category())
                            .price(layout.price())
                            .status(SeatStatus.AVAILABLE)
                            .build());
                }
            }
        }

        seatRepository.saveAll(seats);
        log.info("Seeded {} seats for scheduleId={}", seats.size(), scheduleId);
    }

    private SeatDto toDto(Seat seat) {
        return SeatDto.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .category(seat.getCategory())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .build();
    }

    public record SeatSeedInfo(String category, String rowPrefix, int rowCount, int seatsPerRow, BigDecimal price) {}
}
