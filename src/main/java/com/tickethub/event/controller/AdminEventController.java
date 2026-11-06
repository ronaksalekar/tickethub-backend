package com.tickethub.event.controller;

import com.tickethub.common.dto.ApiResponse;
import com.tickethub.event.dto.CreateEventRequest;
import com.tickethub.event.dto.CreateVenueRequest;
import com.tickethub.event.dto.EventResponse;
import com.tickethub.event.model.Venue;
import com.tickethub.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Operations", description = "Admin-only management endpoints for venues and events")
public class AdminEventController {

    private final EventService eventService;

    @PostMapping("/venues")
    @Operation(summary = "Create venue (Admin)", description = "Creates a new venue with maximum seating capacity.")
    public ResponseEntity<ApiResponse<Venue>> createVenue(@Valid @RequestBody CreateVenueRequest request) {
        Venue venue = eventService.createVenue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Venue created successfully", venue));
    }

    @PostMapping("/events")
    @Operation(summary = "Create event (Admin)", description = "Creates a new event and auto-populates its initial seat inventory.")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse event = eventService.createEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Event created successfully", event));
    }
}
