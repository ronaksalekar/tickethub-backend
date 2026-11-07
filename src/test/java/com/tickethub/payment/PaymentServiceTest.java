package com.tickethub.payment;

import com.tickethub.payment.dto.PaymentRequest;
import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    @Test
    @DisplayName("Should process payment successfully with valid card")
    void shouldProcessPaymentSuccessfully() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(BigDecimal.valueOf(75.50))
                .paymentMethod("CREDIT_CARD")
                .cardNumber("4111222233334444")
                .shouldSucceed(true)
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getTransactionId());
        assertTrue(response.getTransactionId().startsWith("TXN-"));
    }

    @Test
    @DisplayName("Should return failed payment response when shouldSucceed is false")
    void shouldReturnFailedResponseWhenSimulatedFailure() {
        PaymentRequest request = PaymentRequest.builder()
                .amount(BigDecimal.valueOf(75.50))
                .shouldSucceed(false)
                .build();

        PaymentResponse response = paymentService.processPayment(request);

        assertNotNull(response);
        assertFalse(response.isSuccessful());
        assertEquals("FAILED", response.getStatus());
    }
}
