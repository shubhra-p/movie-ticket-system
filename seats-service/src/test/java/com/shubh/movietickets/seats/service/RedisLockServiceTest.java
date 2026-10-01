package com.shubh.movietickets.seats.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLockService redisLockService;

    private UUID scheduleId;
    private UUID userId;
    private List<String> seatNumbers;

    @BeforeEach
    void setUp() {
        redisLockService = new RedisLockService(redisTemplate);
        ReflectionTestUtils.setField(redisLockService, "lockTtlSeconds", 300);

        scheduleId = UUID.randomUUID();
        userId = UUID.randomUUID();
        seatNumbers = List.of("A1", "A2");
    }

    @Test
    @DisplayName("lockSeats — all seats locked successfully (Lua returns 1)")
    void lockSeats_allLocked_returnsTrue() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(1L);

        boolean result = redisLockService.lockSeats(scheduleId, seatNumbers, userId);

        assertThat(result).isTrue();
        verify(redisTemplate).execute(any(RedisScript.class), anyList(),
                eq(userId.toString()), eq("300"));
    }

    @Test
    @DisplayName("lockSeats — contention (Lua returns 0, rollback happened in Redis)")
    void lockSeats_contention_returnsFalse() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(0L);

        boolean result = redisLockService.lockSeats(scheduleId, seatNumbers, userId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("lockSeats — null result from Redis treated as failure")
    void lockSeats_nullResult_returnsFalse() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(null);

        boolean result = redisLockService.lockSeats(scheduleId, seatNumbers, userId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("lockSeats — passes correct keys (seat-lock:{scheduleId}:{seatNumber})")
    @SuppressWarnings("unchecked")
    void lockSeats_correctKeyFormat() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(1L);

        redisLockService.lockSeats(scheduleId, seatNumbers, userId);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any(), any());

        List<String> keys = keysCaptor.getValue();
        assertThat(keys).containsExactly(
                "seat-lock:" + scheduleId + ":A1",
                "seat-lock:" + scheduleId + ":A2"
        );
    }

    @Test
    @DisplayName("unlockSeats — returns number of unlocked seats")
    void unlockSeats_returnsCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(2L);

        long result = redisLockService.unlockSeats(scheduleId, seatNumbers, userId);

        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("unlockSeats — null result returns 0")
    void unlockSeats_nullResult_returnsZero() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(null);

        long result = redisLockService.unlockSeats(scheduleId, seatNumbers, userId);

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("deleteLocksForSeats — deletes correct keys unconditionally")
    void deleteLocksForSeats_deletesCorrectKeys() {
        redisLockService.deleteLocksForSeats(scheduleId, seatNumbers);

        verify(redisTemplate).delete(List.of(
                "seat-lock:" + scheduleId + ":A1",
                "seat-lock:" + scheduleId + ":A2"
        ));
    }

    @Test
    @DisplayName("isSeatLocked — returns true when key exists")
    void isSeatLocked_keyExists_returnsTrue() {
        String key = "seat-lock:" + scheduleId + ":A1";
        when(redisTemplate.hasKey(key)).thenReturn(true);

        boolean result = redisLockService.isSeatLocked(scheduleId, "A1");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isSeatLocked — returns false when key absent")
    void isSeatLocked_keyAbsent_returnsFalse() {
        String key = "seat-lock:" + scheduleId + ":A1";
        when(redisTemplate.hasKey(key)).thenReturn(false);

        boolean result = redisLockService.isSeatLocked(scheduleId, "A1");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getLockOwner — returns userId when lock exists")
    void getLockOwner_lockExists_returnsUserId() {
        String key = "seat-lock:" + scheduleId + ":A1";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(userId.toString());

        Optional<String> result = redisLockService.getLockOwner(scheduleId, "A1");

        assertThat(result).isPresent().contains(userId.toString());
    }

    @Test
    @DisplayName("getLockOwner — returns empty when no lock")
    void getLockOwner_noLock_returnsEmpty() {
        String key = "seat-lock:" + scheduleId + ":A1";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        Optional<String> result = redisLockService.getLockOwner(scheduleId, "A1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getLockTtlSeconds — returns configured TTL")
    void getLockTtlSeconds_returnsConfigured() {
        assertThat(redisLockService.getLockTtlSeconds()).isEqualTo(300);
    }
}
