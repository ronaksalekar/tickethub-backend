package com.tickethub.notification.consumer;

import com.tickethub.notification.event.BookingConfirmedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationConsumer {

    public static final String TOPIC_BOOKING_CONFIRMATIONS = "booking-confirmations";

    @KafkaListener(
            topics = TOPIC_BOOKING_CONFIRMATIONS,
            groupId = "tickethub-notification-group"
    )
    public void handleBookingConfirmation(BookingConfirmedEvent event) {
        log.info("=================================================================");
        log.info("📧 [KAFKA NOTIFICATION CONSUMER] Dispatched Booking Confirmation");
        log.info("Booking Reference : {}", event.getBookingReference());
        log.info("Recipient Email   : {}", event.getUserEmail());
        log.info("Recipient Name    : {}", event.getUserFullName());
        log.info("Event             : {}", event.getEventTitle());
        log.info("Venue             : {}", event.getVenueName());
        log.info("Seat              : {}", event.getSeatNumber());
        log.info("Amount Paid       : ${}", event.getAmount());
        log.info("Transaction ID    : {}", event.getPaymentTransactionId());
        log.info("Confirmed At      : {}", event.getConfirmedAt());
        log.info("=================================================================");
    }
}
