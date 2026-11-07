package com.tickethub.booking.dto;

import com.tickethub.payment.dto.PaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmBookingRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotNull(message = "Seat ID is required")
    private Long seatId;

    @NotNull(message = "Payment details are required")
    @Valid
    private PaymentRequest paymentRequest;
}
