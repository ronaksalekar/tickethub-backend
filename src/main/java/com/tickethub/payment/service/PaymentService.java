package com.tickethub.payment.service;

import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class PaymentService {

    /**
     * Simulates payment gateway processing (Stripe / PayPal style).
     */
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing simulated payment for amount: {} via {}", request.getAmount(), request.getPaymentMethod());

        // Check for simulated failure flag or dummy test declined card
        if (Boolean.FALSE.equals(request.getShouldSucceed()) || "0000000000000000".equals(request.getCardNumber())) {
            log.warn("Simulated payment failed for amount: {}", request.getAmount());
            return PaymentResponse.builder()
                    .transactionId(UUID.randomUUID().toString())
                    .status("FAILED")
                    .message("Card declined or simulated payment failure")
                    .timestamp(Instant.now())
                    .build();
        }

        String transactionId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        log.info("Payment successful. Transaction ID: {}", transactionId);

        return PaymentResponse.builder()
                .transactionId(transactionId)
                .status("SUCCESS")
                .message("Payment processed successfully")
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Compensating transaction: Simulates issuing a refund if checkout fails after charging.
     */
    public void refundPayment(String transactionId, BigDecimal amount) {
        log.warn("Compensating transaction: Refunding amount {} for transaction {}", amount, transactionId);
    }
}
