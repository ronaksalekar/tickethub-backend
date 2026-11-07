package com.tickethub.concurrency;

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

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingConcurrencyStressTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeatHoldService seatHoldService;

    // Simulated thread-safe in-memory Redis state to model atomic SETNX
    private final ConcurrentHashMap<String, String> simulatedRedisStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        simulatedRedisStore.clear();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Simulate atomic Redis SET key value NX EX
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    String val = invocation.getArgument(1);
                    return simulatedRedisStore.putIfAbsent(key, val) == null;
                });

        when(valueOperations.get(any()))
                .thenAnswer(invocation -> simulatedRedisStore.get(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("50 parallel threads compete for 1 seat at the exact same millisecond: Exactly 1 succeeds, 49 fail")
    void shouldPreventDoubleHoldUnderConcurrentTrafficSpike() throws InterruptedException {
        int numberOfConcurrentUsers = 50;
        Long eventId = 1L;
        Long seatId = 99L;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentUsers);
        CountDownLatch readyLatch = new CountDownLatch(numberOfConcurrentUsers);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfConcurrentUsers);

        AtomicInteger successfulHolds = new AtomicInteger(0);
        AtomicInteger rejectedHolds = new AtomicInteger(0);

        for (int i = 1; i <= numberOfConcurrentUsers; i++) {
            final long userId = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Wait for starter gun so all 50 threads fire simultaneously
                    startGun.await();

                    SeatHoldResponse response = seatHoldService.holdSeat(eventId, seatId, userId);
                    if (response != null) {
                        successfulHolds.incrementAndGet();
                    }
                } catch (SeatAlreadyHeldException ex) {
                    rejectedHolds.incrementAndGet();
                } catch (Exception ex) {
                    // unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait until all 50 threads are waiting at the start line
        readyLatch.await(5, TimeUnit.SECONDS);

        // Fire the gun! All 50 threads execute at once
        startGun.countDown();

        // Wait for all 50 threads to complete
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verification of Zero Double-Booking
        assertEquals(true, finished, "All threads should finish execution");
        assertEquals(1, successfulHolds.get(), "CRITICAL: Exactly 1 user must acquire the seat hold");
        assertEquals(49, rejectedHolds.get(), "CRITICAL: Exactly 49 users must be rejected with SeatAlreadyHeldException");
        assertEquals(1, simulatedRedisStore.size(), "Only 1 hold key must exist in Redis store");

        System.out.println("=========================================================");
        System.out.println("CONCURRENCY STRESS TEST RESULT:");
        System.out.println("Concurrent requests: " + numberOfConcurrentUsers);
        System.out.println("Successful bookings/holds: " + successfulHolds.get());
        System.out.println("Blocked double-bookings:  " + rejectedHolds.get());
        System.out.println("Double booking rate:       0.00% (ZERO DOUBLE BOOKINGS)");
        System.out.println("=========================================================");
    }
}
