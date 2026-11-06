package com.tickethub.seat;

import com.tickethub.common.exception.BadRequestException;
import com.tickethub.common.exception.ResourceNotFoundException;
import com.tickethub.event.model.Event;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.seat.dto.SeatHoldResponse;
import com.tickethub.seat.dto.SeatResponse;
import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import com.tickethub.seat.repository.SeatRepository;
import com.tickethub.seat.service.SeatHoldService;
import com.tickethub.seat.service.SeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatHoldService seatHoldService;

    @InjectMocks
    private SeatService seatService;

    private Event event;
    private Seat availableSeat;
    private Seat bookedSeat;

    @BeforeEach
    void setUp() {
        event = Event.builder().id(1L).title("Concert").build();

        availableSeat = Seat.builder()
                .id(10L)
                .event(event)
                .seatNumber("S-1")
                .price(BigDecimal.valueOf(100.00))
                .status(SeatStatus.AVAILABLE)
                .build();

        bookedSeat = Seat.builder()
                .id(11L)
                .event(event)
                .seatNumber("S-2")
                .price(BigDecimal.valueOf(100.00))
                .status(SeatStatus.BOOKED)
                .build();
    }

    @Test
    @DisplayName("Should aggregate seat status from DB and Redis hold state")
    void shouldAggregateSeatStatuses() {
        when(eventRepository.existsById(1L)).thenReturn(true);
        when(seatRepository.findByEventId(1L)).thenReturn(List.of(availableSeat, bookedSeat));
        // Seat 10 is held in Redis by user 42
        when(seatHoldService.getHoldOwner(1L, 10L)).thenReturn(Optional.of(42L));
        when(seatHoldService.getRemainingTtl(1L, 10L)).thenReturn(240L);

        List<SeatResponse> responses = seatService.getSeatsForEvent(1L, 42L);

        assertEquals(2, responses.size());

        // Seat 10: HELD by user 42
        SeatResponse heldSeat = responses.get(0);
        assertEquals("HELD", heldSeat.getStatus());
        assertTrue(heldSeat.isHeldByCurrentUser());
        assertEquals(240L, heldSeat.getHoldRemainingTtlSeconds());

        // Seat 11: BOOKED in DB
        SeatResponse booked = responses.get(1);
        assertEquals("BOOKED", booked.getStatus());
        assertFalse(booked.isHeldByCurrentUser());
    }

    @Test
    @DisplayName("Should throw BadRequestException if attempting to hold already BOOKED seat")
    void shouldThrowWhenHoldingBookedSeat() {
        when(seatRepository.findById(11L)).thenReturn(Optional.of(bookedSeat));

        assertThrows(BadRequestException.class, () ->
                seatService.holdSeat(1L, 11L, 42L)
        );
        verify(seatHoldService, never()).holdSeat(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should delegate to seatHoldService when holding an AVAILABLE seat")
    void shouldDelegateHoldToSeatHoldService() {
        when(seatRepository.findById(10L)).thenReturn(Optional.of(availableSeat));
        when(seatHoldService.holdSeat(1L, 10L, 42L))
                .thenReturn(SeatHoldResponse.builder().seatId(10L).heldByUserId(42L).build());

        SeatHoldResponse response = seatService.holdSeat(1L, 10L, 42L);

        assertNotNull(response);
        assertEquals(10L, response.getSeatId());
        verify(seatHoldService, times(1)).holdSeat(1L, 10L, 42L);
    }
}
