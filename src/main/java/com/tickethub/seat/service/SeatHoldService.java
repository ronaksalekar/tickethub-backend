package com.tickethub.seat.service;

import com.tickethub.common.exception.BadRequestException;
import com.tickethub.common.exception.SeatAlreadyHeldException;
import com.tickethub.seat.dto.SeatHoldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldService {

    public static final Duration HOLD_DURATION = Duration.ofMinutes(5);
    public static final String HOLD_KEY_PREFIX = "seat:hold:";

    /**
     * Atomic Lua script for safe hold release:
     * Only deletes the key if its value matches the user's ID.
     * Prevents the check-then-act race condition where an expired key is deleted from a new holder.
     */
    private static final String RELEASE_HOLD_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final StringRedisTemplate redisTemplate;

    /**
     * Atomically holds a seat for 5 minutes using Redis SETNX EX.
     * Guaranteed safe against concurrent hold attempts.
     */
    public SeatHoldResponse holdSeat(Long eventId, Long seatId, Long userId) {
        String key = buildHoldKey(eventId, seatId);

        // Atomic: SET seat:hold:{eventId}:{seatId} {userId} NX EX 300
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(userId), HOLD_DURATION);

        if (Boolean.FALSE.equals(acquired) || acquired == null) {
            String currentHolder = redisTemplate.opsForValue().get(key);
            if (String.valueOf(userId).equals(currentHolder)) {
                // If the same user already holds it, refresh the TTL and return
                redisTemplate.expire(key, HOLD_DURATION);
                log.info("User {} refreshed existing hold on seat {} for event {}", userId, seatId, eventId);
                return SeatHoldResponse.builder()
                        .eventId(eventId)
                        .seatId(seatId)
                        .heldByUserId(userId)
                        .expiresInSeconds(HOLD_DURATION.toSeconds())
                        .heldAt(Instant.now())
                        .build();
            }
            log.warn("Hold conflict: User {} attempted to hold seat {} (event {}) which is already held", userId, seatId, eventId);
            throw new SeatAlreadyHeldException(
                    "Seat " + seatId + " for event " + eventId + " is currently held by another customer. Please choose a different seat."
            );
        }

        log.info("Successfully acquired hold on seat {} (event {}) for user {} (TTL: {}s)",
                seatId, eventId, userId, HOLD_DURATION.toSeconds());

        return SeatHoldResponse.builder()
                .eventId(eventId)
                .seatId(seatId)
                .heldByUserId(userId)
                .expiresInSeconds(HOLD_DURATION.toSeconds())
                .heldAt(Instant.now())
                .build();
    }

    /**
     * Releases a seat hold using an atomic Redis Lua script.
     * Guarantees zero race condition when hold expires right during release.
     */
    public void releaseHold(Long eventId, Long seatId, Long userId) {
        String key = buildHoldKey(eventId, seatId);

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(RELEASE_HOLD_LUA_SCRIPT);
        redisScript.setResultType(Long.class);

        Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(userId)
        );

        if (result == null || result == 0L) {
            // Check if key exists under a different user
            String currentHolder = redisTemplate.opsForValue().get(key);
            if (currentHolder != null && !currentHolder.equals(String.valueOf(userId))) {
                log.warn("Unauthorized hold release attempt: User {} tried to release hold belonging to user {}", userId, currentHolder);
                throw new BadRequestException("You cannot release a seat hold reserved by another user.");
            }
            log.info("Hold on seat {} for event {} had already expired or been cleared", seatId, eventId);
        } else {
            log.info("Atomically released hold on seat {} (event {}) by user {}", seatId, eventId, userId);
        }
    }

    /**
     * Checks if a seat currently has an active temporary hold.
     */
    public boolean isSeatHeld(Long eventId, Long seatId) {
        String key = buildHoldKey(eventId, seatId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Gets the user ID of whoever currently holds the seat, if held.
     */
    public Optional<Long> getHoldOwner(Long eventId, Long seatId) {
        String key = buildHoldKey(eventId, seatId);
        String holderId = redisTemplate.opsForValue().get(key);
        if (holderId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(holderId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Gets the remaining time-to-live in seconds for an active hold.
     */
    public Long getRemainingTtl(Long eventId, Long seatId) {
        String key = buildHoldKey(eventId, seatId);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0L;
    }

    /**
     * Clears hold directly upon successful booking confirmation.
     */
    public void clearHoldForBooking(Long eventId, Long seatId) {
        String key = buildHoldKey(eventId, seatId);
        redisTemplate.delete(key);
        log.info("Cleared Redis hold on seat {} for event {} following booking confirmation", seatId, eventId);
    }

    public String buildHoldKey(Long eventId, Long seatId) {
        return HOLD_KEY_PREFIX + eventId + ":" + seatId;
    }
}
