package com.tickethub.seat.controller;

import com.tickethub.auth.model.User;
import com.tickethub.common.dto.ApiResponse;
import com.tickethub.seat.dto.SeatHoldResponse;
import com.tickethub.seat.dto.SeatResponse;
import com.tickethub.seat.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events/{eventId}/seats")
@RequiredArgsConstructor
@Tag(name = "Seats & Holds", description = "Real-time seat inventory and temporary hold operations")
public class SeatController {

    private final SeatService seatService;

    @GetMapping
    @Operation(
            summary = "Get seat inventory for event",
            description = "Returns all seats for an event with real-time status (AVAILABLE, HELD with TTL, or BOOKED)."
    )
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeats(
            @PathVariable Long eventId,
            @AuthenticationPrincipal User currentUser
    ) {
        Long currentUserId = (currentUser != null) ? currentUser.getId() : null;
        List<SeatResponse> seats = seatService.getSeatsForEvent(eventId, currentUserId);
        return ResponseEntity.ok(ApiResponse.ok("Seat inventory retrieved successfully", seats));
    }

    @PostMapping("/{seatId}/hold")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Place a 5-minute hold on a seat",
            description = "Atomically locks the seat in Redis with a 5-minute TTL for the authenticated user."
    )
    public ResponseEntity<ApiResponse<SeatHoldResponse>> holdSeat(
            @PathVariable Long eventId,
            @PathVariable Long seatId,
            @AuthenticationPrincipal User currentUser
    ) {
        SeatHoldResponse response = seatService.holdSeat(eventId, seatId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok("Seat held successfully for 5 minutes", response));
    }

    @DeleteMapping("/{seatId}/hold")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Release a seat hold",
            description = "Releases an active hold before the 5-minute TTL expires. Only the holding user can release."
    )
    public ResponseEntity<ApiResponse<Void>> releaseHold(
            @PathVariable Long eventId,
            @PathVariable Long seatId,
            @AuthenticationPrincipal User currentUser
    ) {
        seatService.releaseHold(eventId, seatId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok("Seat hold released successfully", null));
    }
}
