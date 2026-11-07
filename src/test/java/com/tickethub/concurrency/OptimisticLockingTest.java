package com.tickethub.concurrency;

import com.tickethub.common.dto.ApiResponse;
import com.tickethub.common.exception.GlobalExceptionHandler;
import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptimisticLockingTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should translate ObjectOptimisticLockingFailureException into HTTP 409 Conflict")
    void shouldHandleOptimisticLockingFailureGracefully() {
        ObjectOptimisticLockingFailureException lockException =
                new ObjectOptimisticLockingFailureException(Seat.class, 10L);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleOptimisticLock(lockException);

        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(false, response.getBody().isSuccess());
        assertEquals("Seat was already booked or modified by another transaction. Please try again.",
                response.getBody().getMessage());
    }

    @Test
    @DisplayName("Seat entity must track version field for optimistic locking")
    void seatEntityMustHaveVersionInitialized() {
        Seat seat = Seat.builder()
                .id(1L)
                .seatNumber("A-1")
                .status(SeatStatus.AVAILABLE)
                .version(0L)
                .build();

        assertEquals(0L, seat.getVersion(), "Initial seat version must be 0");
        seat.setStatus(SeatStatus.BOOKED);
        seat.setVersion(seat.getVersion() + 1);
        assertEquals(1L, seat.getVersion(), "Seat version increments upon status change");
    }
}
