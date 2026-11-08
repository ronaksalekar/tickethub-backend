package com.tickethub.booking.service;

import com.tickethub.auth.model.User;
import com.tickethub.auth.repository.UserRepository;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.ConfirmBookingRequest;
import com.tickethub.booking.model.Booking;
import com.tickethub.booking.model.BookingStatus;
import com.tickethub.booking.repository.BookingRepository;
import com.tickethub.common.exception.BadRequestException;
import com.tickethub.common.exception.PaymentFailedException;
import com.tickethub.common.exception.ResourceNotFoundException;
import com.tickethub.notification.consumer.NotificationConsumer;
import com.tickethub.notification.event.BookingConfirmedEvent;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import com.tickethub.seat.repository.SeatRepository;
import com.tickethub.seat.service.SeatHoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final SeatHoldService seatHoldService;
    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Confirms an active seat hold into a permanent booking.
     * Architectural Best Practice:
     * 1. Payment processing executes OUTSIDE the database transaction to prevent HikariCP connection pool exhaustion.
     * 2. Short, focused DB transaction commits seat status with Optimistic Locking (@Version).
     * 3. Asynchronously emits BookingConfirmedEvent to Kafka for decoupled notifications.
     */
    public BookingResponse confirmBooking(Long userId, ConfirmBookingRequest request) {
        Long eventId = request.getEventId();
        Long seatId = request.getSeatId();

        // Step 0: Idempotent guard — if user already completed this booking (e.g. duplicate click or network retry)
        Optional<Booking> existingBooking = bookingRepository.findByUserIdAndSeatId(userId, seatId);
        if (existingBooking.isPresent()) {
            log.info("Idempotent booking hit: Seat {} was already booked by user {}. Returning existing booking.", seatId, userId);
            return BookingResponse.fromEntity(existingBooking.get(), "TXN-CONFIRMED");
        }

        // Step 1: Pre-validation of Redis hold
        Optional<Long> holdOwner = seatHoldService.getHoldOwner(eventId, seatId);
        if (holdOwner.isEmpty()) {
            throw new BadRequestException("Seat hold has expired or was not reserved. Holds expire after 5 minutes.");
        }
        if (!holdOwner.get().equals(userId)) {
            throw new BadRequestException("This seat is currently held by another customer.");
        }

        // Step 2: Validate seat and user exist before charging
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + seatId));

        if (!seat.getEvent().getId().equals(eventId)) {
            throw new BadRequestException("Seat " + seatId + " does not belong to event " + eventId);
        }

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new BadRequestException("Seat " + seat.getSeatNumber() + " is already permanently booked.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        request.getPaymentRequest().setAmount(seat.getPrice());

        // Step 3: Process Payment OUTSIDE the DB transaction (avoids pool starvation)
        PaymentResponse paymentResponse = paymentService.processPayment(request.getPaymentRequest());
        if (!paymentResponse.isSuccessful()) {
            seatHoldService.releaseHold(eventId, seatId, userId);
            throw new PaymentFailedException("Payment failed: " + paymentResponse.getMessage());
        }

        // Step 4: Short, focused database transaction with @Version optimistic locking
        Booking booking;
        try {
            booking = executeBookingCommit(user, seat);
        } catch (Exception ex) {
            // Compensating transaction: refund user if DB commit fails after payment
            log.error("Database commit failed after payment. Triggering automated refund for user {}", userId, ex);
            paymentService.refundPayment(paymentResponse.getTransactionId(), seat.getPrice());
            throw ex;
        }

        // Step 5: Clean up temporary hold in Redis
        seatHoldService.clearHoldForBooking(eventId, seatId);

        // Step 6: Publish event asynchronously to Kafka (decoupled from the HTTP response)
        publishBookingConfirmedEvent(booking, user, paymentResponse.getTransactionId());

        log.info("Booking confirmed successfully: Reference {} for user {} on seat {}",
                booking.getBookingReference(), userId, seat.getSeatNumber());

        return BookingResponse.fromEntity(booking, paymentResponse.getTransactionId());
    }

    /**
     * Isolated database transaction: Keeps DB connection acquisition window as short as possible.
     */
    @Transactional
    public Booking executeBookingCommit(User user, Seat seat) {
        // Optimistic Locking (@Version) increments automatically on this update
        seat.setStatus(SeatStatus.BOOKED);
        seatRepository.save(seat);

        String bookingReference = "TH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Booking booking = Booking.builder()
                .user(user)
                .event(seat.getEvent())
                .seat(seat)
                .amount(seat.getPrice())
                .status(BookingStatus.CONFIRMED)
                .bookingReference(bookingReference)
                .build();

        return bookingRepository.save(booking);
    }

    private void publishBookingConfirmedEvent(Booking booking, User user, String transactionId) {
        try {
            BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                    .bookingId(booking.getId())
                    .bookingReference(booking.getBookingReference())
                    .userId(user.getId())
                    .userEmail(user.getEmail())
                    .userFullName(user.getFullName())
                    .eventId(booking.getEvent().getId())
                    .eventTitle(booking.getEvent().getTitle())
                    .venueName(booking.getEvent().getVenue().getName())
                    .seatId(booking.getSeat().getId())
                    .seatNumber(booking.getSeat().getSeatNumber())
                    .amount(booking.getAmount())
                    .paymentTransactionId(transactionId)
                    .confirmedAt(Instant.now())
                    .build();

            kafkaTemplate.send(NotificationConsumer.TOPIC_BOOKING_CONFIRMATIONS, booking.getBookingReference(), event);
            log.info("Published BookingConfirmedEvent to Kafka topic '{}' for ref: {}",
                    NotificationConsumer.TOPIC_BOOKING_CONFIRMATIONS, booking.getBookingReference());
        } catch (Exception ex) {
            // Non-blocking: Notification failure must never roll back a successful booking
            log.error("Failed to publish booking confirmation event to Kafka for ref: {}",
                    booking.getBookingReference(), ex);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getUserBookings(Long userId) {
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            throw new BadRequestException("You can only cancel your own bookings.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking " + booking.getBookingReference() + " is already cancelled.");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Seat seat = booking.getSeat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seatRepository.save(seat);

        log.info("Booking {} cancelled by user {}. Seat {} reverted to AVAILABLE.",
                booking.getBookingReference(), userId, seat.getSeatNumber());

        return BookingResponse.fromEntity(booking);
    }
}
