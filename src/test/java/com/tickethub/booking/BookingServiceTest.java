package com.tickethub.booking;

import com.tickethub.auth.model.Role;
import com.tickethub.auth.model.User;
import com.tickethub.auth.repository.UserRepository;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.ConfirmBookingRequest;
import com.tickethub.booking.model.Booking;
import com.tickethub.booking.model.BookingStatus;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.booking.service.BookingService;
import com.tickethub.common.exception.BadRequestException;
import com.tickethub.common.exception.PaymentFailedException;
import com.tickethub.event.model.Event;
import com.tickethub.event.model.Venue;
import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import com.tickethub.seat.repository.SeatRepository;
import com.tickethub.seat.service.SeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SeatHoldService seatHoldService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private Venue venue;
    private Event event;
    private Seat seat;
    private ConfirmBookingRequest request;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("user@example.com").fullName("Test User").role(Role.ROLE_USER).build();
        venue = Venue.builder().id(1L).name("Madison Square Garden").city("New York").totalCapacity(20000).build();
        event = Event.builder().id(1L).venue(venue).title("Rock Concert").eventTime(Instant.now()).totalSeats(100).build();
        seat = Seat.builder().id(10L).event(event).seatNumber("S-10").price(BigDecimal.valueOf(150.00)).status(SeatStatus.AVAILABLE).version(0L).build();

        request = ConfirmBookingRequest.builder()
                .eventId(1L)
                .seatId(10L)
                .paymentRequest(PaymentRequest.builder().amount(BigDecimal.valueOf(150.00)).build())
                .build();
    }

    @Test
    @DisplayName("Should successfully confirm booking and clear Redis hold")
    void shouldConfirmBookingSuccessfully() {
        when(seatHoldService.getHoldOwner(1L, 10L)).thenReturn(Optional.of(1L));
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenReturn(PaymentResponse.builder().transactionId("TXN-123456").status("SUCCESS").build());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(500L);
            return b;
        });

        BookingResponse response = bookingService.confirmBooking(1L, request);

        assertNotNull(response);
        assertEquals(SeatStatus.BOOKED, seat.getStatus());
        assertEquals("CONFIRMED", response.getStatus());
        assertEquals("TXN-123456", response.getPaymentTransactionId());
        assertTrue(response.getBookingReference().startsWith("TH-"));

        verify(seatRepository, times(1)).save(seat);
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(seatHoldService, times(1)).clearHoldForBooking(1L, 10L);
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should reject booking if hold has expired")
    void shouldRejectWhenHoldExpired() {
        when(seatHoldService.getHoldOwner(1L, 10L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () ->
                bookingService.confirmBooking(1L, request)
        );

        verify(paymentService, never()).processPayment(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject booking if hold belongs to another user")
    void shouldRejectWhenHoldBelongsToAnotherUser() {
        when(seatHoldService.getHoldOwner(1L, 10L)).thenReturn(Optional.of(999L)); // Another user

        assertThrows(BadRequestException.class, () ->
                bookingService.confirmBooking(1L, request)
        );

        verify(paymentService, never()).processPayment(any());
    }

    @Test
    @DisplayName("Should release hold and throw PaymentFailedException when payment fails")
    void shouldReleaseHoldWhenPaymentFails() {
        when(seatHoldService.getHoldOwner(1L, 10L)).thenReturn(Optional.of(1L));
        when(seatRepository.findById(10L)).thenReturn(Optional.of(seat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenReturn(PaymentResponse.builder().status("FAILED").message("Insufficient funds").build());

        assertThrows(PaymentFailedException.class, () ->
                bookingService.confirmBooking(1L, request)
        );

        verify(seatHoldService, times(1)).releaseHold(1L, 10L, 1L);
        verify(bookingRepository, never()).save(any());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
    }

    @Test
    @DisplayName("Should cancel booking and restore seat to AVAILABLE")
    void shouldCancelBookingAndRestoreSeat() {
        seat.setStatus(SeatStatus.BOOKED);
        Booking booking = Booking.builder()
                .id(500L)
                .user(user)
                .event(event)
                .seat(seat)
                .amount(BigDecimal.valueOf(150.00))
                .status(BookingStatus.CONFIRMED)
                .bookingReference("TH-REF123")
                .build();

        when(bookingRepository.findById(500L)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.cancelBooking(500L, 1L);

        assertEquals("CANCELLED", response.getStatus());
        assertEquals(SeatStatus.AVAILABLE, seat.getStatus());
        verify(bookingRepository, times(1)).save(booking);
        verify(seatRepository, times(1)).save(seat);
    }
}
