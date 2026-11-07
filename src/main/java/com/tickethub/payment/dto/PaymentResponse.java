package com.tickethub.payment.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String transactionId;
    private String status; // SUCCESS, FAILED
    private String message;
    @Builder.Default
    private Instant timestamp = Instant.now();

    public boolean isSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
