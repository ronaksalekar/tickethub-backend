package com.tickethub.seat;

import com.tickethub.common.exception.BadRequestException;
import com.tickethub.common.exception.SeatAlreadyHeldException;
import com.tickethub.seat.dto.SeatHoldResponse;
import com.tickethub.seat.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeatHoldService seatHoldService;

    private final Long eventId = 1L;
    private final Long seatId = 100L;
    private final Long userId = 42L;
    private final String expectedKey = "seat:hold:1:100";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should successfully acquire seat hold when seat is unheld")
    void shouldAcquireHoldSuccessfully() {
        when(valueOperations.setIfAbsent(eq(expectedKey), eq(String.valueOf(userId)), eq(Duration.ofMinutes(5))))
                .thenReturn(true);

        SeatHoldResponse response = seatHoldService.holdSeat(eventId, seatId, userId);

        assertNotNull(response);
        assertEquals(eventId, response.getEventId());
        assertEquals(seatId, response.getSeatId());
        assertEquals(userId, response.getHeldByUserId());
        assertEquals(300L, response.getExpiresInSeconds());
        verify(valueOperations, times(1)).setIfAbsent(expectedKey, String.valueOf(userId), Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Should throw SeatAlreadyHeldException when seat is already held by another user")
    void shouldThrowWhenSeatHeldByAnotherUser() {
        when(valueOperations.setIfAbsent(eq(expectedKey), eq(String.valueOf(userId)), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get(expectedKey)).thenReturn("999"); // Held by user 999

        assertThrows(SeatAlreadyHeldException.class, () ->
                seatHoldService.holdSeat(eventId, seatId, userId)
        );
    }

    @Test
    @DisplayName("Should atomically release hold via Lua script when requested by the hold owner")
    void shouldReleaseHoldWhenOwnerRequests() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(1L); // Lua script deleted 1 key

        assertDoesNotThrow(() -> seatHoldService.releaseHold(eventId, seatId, userId));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("Should throw BadRequestException when non-owner tries to release hold")
    void shouldThrowWhenNonOwnerTriesToReleaseHold() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(0L); // Lua script did not delete
        when(valueOperations.get(expectedKey)).thenReturn("999"); // Owner is user 999

        assertThrows(BadRequestException.class, () ->
                seatHoldService.releaseHold(eventId, seatId, userId)
        );
    }
}
