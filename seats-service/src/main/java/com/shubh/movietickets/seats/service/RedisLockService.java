package com.shubh.movietickets.seats.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*
 Redis-based distributed lock for seat reservation.

 Lock key format: seat-lock:{scheduleId}:{seatNumber}
 Lock value: userId (so we can verify ownership)
 TTL: configurable (default 5 minutes) — auto-expires if payment not completed

 Two-phase approach:
   1. Redis SET NX EX (fast, distributed lock — first line of defense)
   2. Postgres UPDATE WHERE status=AVAILABLE (durable, optimistic lock — source of truth)

 This dual-layer design means:
   - Redis handles the hot path (sub-ms lock checks, 100k+ ops/sec)
   - Postgres guarantees correctness even if Redis fails
   - TTL auto-releases locks if users abandon checkout
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    @Value("${seats.lock.ttl-seconds:300}")
    private int lockTtlSeconds;

    private static final String LOCK_PREFIX = "seat-lock:";

    // Lua script for atomic multi-key lock acquisition
    // Returns 1 if ALL seats locked successfully, 0 if any failed
    private static final String LOCK_SCRIPT = """
            local lockCount = 0
            for i, key in ipairs(KEYS) do
                local result = redis.call('SET', key, ARGV[1], 'NX', 'EX', ARGV[2])
                if result then
                    lockCount = lockCount + 1
                end
            end
            if lockCount == #KEYS then
                return 1
            else
                -- Rollback: release any locks we acquired
                for i, key in ipairs(KEYS) do
                    local val = redis.call('GET', key)
                    if val == ARGV[1] then
                        redis.call('DEL', key)
                    end
                end
                return 0
            end
            """;

    // Lua script for atomic multi-key unlock (only if owned by userId)
    private static final String UNLOCK_SCRIPT = """
            local unlockCount = 0
            for i, key in ipairs(KEYS) do
                local val = redis.call('GET', key)
                if val == ARGV[1] then
                    redis.call('DEL', key)
                    unlockCount = unlockCount + 1
                end
            end
            return unlockCount
            """;

    /*
     Atomically lock multiple seats for a user.
     Uses a Lua script so either ALL seats are locked or NONE are (all-or-nothing).
     Returns true if all seats were locked successfully.
     */
    public boolean lockSeats(UUID scheduleId, List<String> seatNumbers, UUID userId) {
        List<String> keys = seatNumbers.stream()
                .map(seat -> buildKey(scheduleId, seat))
                .toList();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);

        Long result = redisTemplate.execute(script, keys,
                userId.toString(),
                String.valueOf(lockTtlSeconds));

        boolean locked = result != null && result == 1L;
        log.info("Redis lock attempt for scheduleId={}, seats={}, userId={}, result={}",
                scheduleId, seatNumbers, userId, locked ? "LOCKED" : "FAILED");
        return locked;
    }

    /*
     Atomically unlock multiple seats (only if owned by the given userId).
     Returns the number of seats actually unlocked.
     */
    public long unlockSeats(UUID scheduleId, List<String> seatNumbers, UUID userId) {
        List<String> keys = seatNumbers.stream()
                .map(seat -> buildKey(scheduleId, seat))
                .toList();

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

        Long result = redisTemplate.execute(script, keys, userId.toString());

        log.info("Redis unlock for scheduleId={}, seats={}, userId={}, unlocked={}",
                scheduleId, seatNumbers, userId, result);
        return result != null ? result : 0;
    }

    /*
     Delete lock keys unconditionally (used after booking confirmation).
     */
    public void deleteLocksForSeats(UUID scheduleId, List<String> seatNumbers) {
        List<String> keys = seatNumbers.stream()
                .map(seat -> buildKey(scheduleId, seat))
                .toList();
        redisTemplate.delete(keys);
        log.info("Redis locks deleted for scheduleId={}, seats={}", scheduleId, seatNumbers);
    }

    /*
     Check if a specific seat is locked in Redis.
     */
    public boolean isSeatLocked(UUID scheduleId, String seatNumber) {
        String key = buildKey(scheduleId, seatNumber);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /*
     Get the userId that holds the lock for a seat.
     */
    public Optional<String> getLockOwner(UUID scheduleId, String seatNumber) {
        String key = buildKey(scheduleId, seatNumber);
        String value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value);
    }

    public int getLockTtlSeconds() {
        return lockTtlSeconds;
    }

    private String buildKey(UUID scheduleId, String seatNumber) {
        return LOCK_PREFIX + scheduleId + ":" + seatNumber;
    }
}
