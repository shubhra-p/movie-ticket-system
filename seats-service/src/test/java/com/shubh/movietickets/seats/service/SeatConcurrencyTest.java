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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 🔥 CONCURRENCY TEST — The Resume Proof at Unit Level
 * <p>
 * Proves: When 50 threads simultaneously try to lock the same 2 seats,
 * only 1 thread succeeds and the rest get ConflictException.
 * Zero double-bookings guaranteed.
 * <p>
 * Uses CountDownLatch to synchronize thread start for maximum contention,
 * with a mock RedisLockService that simulates atomic compare-and-set.
 */
@ExtendWith(MockitoExtension.class)
class SeatConcurrencyTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private RedisLockService redisLockService;

    private SeatService seatService;

    private UUID scheduleId;
    private List<String> seatNumbers;

    @BeforeEach
    void setUp() {
        seatService = new SeatService(seatRepository, redisLockService, new SimpleMeterRegistry());
        scheduleId = UUID.randomUUID();
        seatNumbers = List.of("A1", "A2");
    }

    @Test
    @DisplayName("🔥 50 concurrent lock requests — exactly 1 wins, 49 get ConflictException")
    void concurrentLockRequests_onlyOneWins() throws InterruptedException {
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);  // blocks all threads until signal
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        /*
         Simulate Redis lock: only the FIRST caller wins.
         AtomicInteger ensures thread-safety — only one thread gets lockCount == 1.
         */
        AtomicInteger lockAttempts = new AtomicInteger(0);
        when(redisLockService.lockSeats(eq(scheduleId), eq(seatNumbers), any(UUID.class)))
                .thenAnswer(invocation -> lockAttempts.incrementAndGet() == 1);

        /* Only called for the winning thread */
        when(seatRepository.bulkUpdateStatus(eq(scheduleId), eq(seatNumbers),
                eq(SeatStatus.AVAILABLE), eq(SeatStatus.LOCKED), any(UUID.class)))
                .thenReturn(2);

        when(redisLockService.getLockTtlSeconds()).thenReturn(300);

        List<Seat> mockedSeats = List.of(
                Seat.builder().seatNumber("A1").price(BigDecimal.valueOf(200)).build(),
                Seat.builder().seatNumber("A2").price(BigDecimal.valueOf(200)).build()
        );
        when(seatRepository.findByScheduleIdAndSeatNumberIn(eq(scheduleId), eq(seatNumbers)))
                .thenReturn(mockedSeats);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            UUID threadUserId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await(); // all threads wait here until released

                    LockSeatsRequest request = new LockSeatsRequest(scheduleId, threadUserId, seatNumbers);
                    LockSeatsResponse response = seatService.lockSeats(request);

                    if (response.isLocked()) {
                        successCount.incrementAndGet();
                    }
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously for maximum contention
        startLatch.countDown();

        // Wait for all threads to complete (with timeout to prevent hanging)
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All threads should complete within timeout").isTrue();
        assertThat(successCount.get()).as("Exactly 1 thread should successfully lock seats").isEqualTo(1);
        assertThat(conflictCount.get()).as("49 threads should get ConflictException").isEqualTo(49);
        assertThat(errorCount.get()).as("No unexpected errors").isZero();
        assertThat(successCount.get() + conflictCount.get())
                .as("Total outcomes should equal thread count")
                .isEqualTo(threadCount);
    }

    @Test
    @DisplayName("🔥 Concurrent locks on DIFFERENT seats — all succeed (no false contention)")
    void concurrentLockRequests_differentSeats_allSucceed() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        /* Each thread locks different seats, so Redis always returns true */
        when(redisLockService.lockSeats(eq(scheduleId), anyList(), any(UUID.class)))
                .thenReturn(true);
        when(seatRepository.bulkUpdateStatus(eq(scheduleId), anyList(),
                eq(SeatStatus.AVAILABLE), eq(SeatStatus.LOCKED), any(UUID.class)))
                .thenReturn(2);
        when(redisLockService.getLockTtlSeconds()).thenReturn(300);
        when(seatRepository.findByScheduleIdAndSeatNumberIn(eq(scheduleId), anyList()))
                .thenReturn(List.of(
                        Seat.builder().seatNumber("X1").price(BigDecimal.valueOf(200)).build(),
                        Seat.builder().seatNumber("X2").price(BigDecimal.valueOf(200)).build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int row = i;
            UUID threadUserId = UUID.randomUUID();
            char rowChar = (char) ('A' + row);
            List<String> threadSeats = List.of(rowChar + "1", rowChar + "2");

            executor.submit(() -> {
                try {
                    startLatch.await();
                    LockSeatsRequest request = new LockSeatsRequest(scheduleId, threadUserId, threadSeats);
                    seatService.lockSeats(request);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get())
                .as("All threads should succeed since they're locking different seats")
                .isEqualTo(threadCount);
    }

    @Test
    @DisplayName("Redis succeeds but Postgres conflict — Redis rollback under concurrency")
    void concurrentLockRequests_postgresConflict_rollsBackRedis() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger conflictCount = new AtomicInteger(0);

        /* Simulate: Redis lets everyone through (unrealistic but tests Postgres layer) */
        when(redisLockService.lockSeats(eq(scheduleId), eq(seatNumbers), any(UUID.class)))
                .thenReturn(true);

        /* Postgres only allows the first thread's update */
        AtomicInteger pgAttempts = new AtomicInteger(0);
        when(seatRepository.bulkUpdateStatus(eq(scheduleId), eq(seatNumbers),
                eq(SeatStatus.AVAILABLE), eq(SeatStatus.LOCKED), any(UUID.class)))
                .thenAnswer(inv -> pgAttempts.incrementAndGet() == 1 ? 2 : 0);

        when(redisLockService.getLockTtlSeconds()).thenReturn(300);
        when(seatRepository.findByScheduleIdAndSeatNumberIn(eq(scheduleId), eq(seatNumbers)))
                .thenReturn(List.of(
                        Seat.builder().seatNumber("A1").price(BigDecimal.valueOf(200)).build(),
                        Seat.builder().seatNumber("A2").price(BigDecimal.valueOf(200)).build()
                ));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            UUID threadUserId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    seatService.lockSeats(new LockSeatsRequest(scheduleId, threadUserId, seatNumbers));
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(conflictCount.get())
                .as("All but one thread should get ConflictException from Postgres layer")
                .isEqualTo(threadCount - 1);

        /* Verify Redis rollback was called for the Postgres-rejected threads */
        verify(redisLockService, atLeast(threadCount - 1))
                .unlockSeats(eq(scheduleId), eq(seatNumbers), any(UUID.class));
    }
}
