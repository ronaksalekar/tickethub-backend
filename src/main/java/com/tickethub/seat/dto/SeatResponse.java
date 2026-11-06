package com.tickethub.seat.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatResponse {
    private Long id;
    private Long eventId;
    private String seatNumber;
    private BigDecimal price;
    private String status; // AVAILABLE, HELD, BOOKED
    private boolean heldByCurrentUser;
    private Long holdRemainingTtlSeconds;
}
