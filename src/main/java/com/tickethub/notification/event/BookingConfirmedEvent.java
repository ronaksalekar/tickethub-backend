package com.tickethub.notification.event;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingConfirmedEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private Long eventId;
    private String eventTitle;
    private String venueName;
    private Long seatId;
    private String seatNumber;
    private BigDecimal amount;
    private String paymentTransactionId;
    private Instant confirmedAt;
}
