package com.tickethub.common.config;

import com.tickethub.auth.model.Role;
import com.tickethub.auth.model.User;
import com.tickethub.auth.repository.UserRepository;
import com.tickethub.event.dto.CreateEventRequest;
import com.tickethub.event.dto.CreateVenueRequest;
import com.tickethub.event.model.Venue;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.VenueRepository;
import com.tickethub.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final EventService eventService;

    @Override
    public void run(String... args) {
        // 1. Seed Admin
        String adminEmail = "admin@tickethub.com";
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode("Admin@123456"))
                    .fullName("TicketHub Admin")
                    .role(Role.ROLE_ADMIN)
                    .build();
            userRepository.save(admin);
            log.info("Initialized default ADMIN user: {}", adminEmail);
        }

        // 2. Seed Demo Customer User
        String userEmail = "demo@tickethub.com";
        if (!userRepository.existsByEmail(userEmail)) {
            User demoUser = User.builder()
                    .email(userEmail)
                    .password(passwordEncoder.encode("Demo@123456"))
                    .fullName("Alex Morgan")
                    .role(Role.ROLE_USER)
                    .build();
            userRepository.save(demoUser);
            log.info("Initialized default DEMO customer user: {}", userEmail);
        }

        // 3. Seed Sample Venue & Event with initial seats if none exist
        if (venueRepository.count() == 0) {
            Venue venue = eventService.createVenue(CreateVenueRequest.builder()
                    .name("Madison Square Garden")
                    .city("New York")
                    .address("4 Pennsylvania Plaza")
                    .totalCapacity(20000)
                    .build());

            eventService.createEvent(CreateEventRequest.builder()
                    .venueId(venue.getId())
                    .title("Coldplay: Music of the Spheres Tour")
                    .description("Live concert with synchronized LED wristbands and stadium laser visuals.")
                    .eventTime(Instant.now().plus(14, ChronoUnit.DAYS))
                    .totalSeats(40)
                    .defaultSeatPrice(BigDecimal.valueOf(150.00))
                    .build());

            log.info("Initialized demo venue 'Madison Square Garden' and event with 40 seats.");
        }
    }
}
