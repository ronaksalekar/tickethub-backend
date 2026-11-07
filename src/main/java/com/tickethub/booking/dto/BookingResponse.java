package com.tickethub.booking.dto;

import com.tickethub.booking.model.Booking;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {
    private Long id;
    private String bookingReference;
    private Long userId;
    private String userEmail;
    private Long eventId;
    private String eventTitle;
    private String venueName;
    private Long seatId;
    private String seatNumber;
    private BigDecimal amount;
    private String status;
    private Instant createdAt;
    private String paymentTransactionId;

    public static BookingResponse fromEntity(Booking booking, String paymentTransactionId) {
        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUser().getId())
                .userEmail(booking.getUser().getEmail())
                .eventId(booking.getEvent().getId())
                .eventTitle(booking.getEvent().getTitle())
                .venueName(booking.getEvent().getVenue().getName())
                .seatId(booking.getSeat().getId())
                .seatNumber(booking.getSeat().getSeatNumber())
                .amount(booking.getAmount())
                .status(booking.getStatus().name())
                .createdAt(booking.getCreatedAt())
                .paymentTransactionId(paymentTransactionId)
                .build();
    }

    public static BookingResponse fromEntity(Booking booking) {
        return fromEntity(booking, null);
    }
}
