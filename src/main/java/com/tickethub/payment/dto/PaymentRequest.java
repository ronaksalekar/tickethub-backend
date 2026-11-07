package com.tickethub.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "Payment method is required")
    @Builder.Default
    private String paymentMethod = "CREDIT_CARD";

    @NotBlank(message = "Card number or token is required")
    @Builder.Default
    private String cardNumber = "4111222233334444";

    /**
     * Flag to simulate payment gateway failures in integration/load testing.
     */
    @Builder.Default
    private Boolean shouldSucceed = true;
}
