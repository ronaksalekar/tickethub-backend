package com.tickethub.seat.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatHoldResponse {
    private Long eventId;
    private Long seatId;
    private Long heldByUserId;
    private Long expiresInSeconds;
    @Builder.Default
    private Instant heldAt = Instant.now();
}
