package com.tickethub.booking.controller;

import com.tickethub.auth.model.User;
import com.tickethub.booking.dto.BookingResponse;
import com.tickethub.booking.dto.ConfirmBookingRequest;
import com.tickethub.booking.service.BookingService;
import com.tickethub.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Bookings", description = "Booking confirmation, history, and cancellation operations")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/confirm")
    @Operation(
            summary = "Confirm booking",
            description = "Converts an active 5-minute Redis hold into a confirmed database booking, executes mock payment, and commits transaction."
    )
    public ResponseEntity<ApiResponse<BookingResponse>> confirmBooking(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ConfirmBookingRequest request
    ) {
        BookingResponse response = bookingService.confirmBooking(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Booking confirmed successfully", response));
    }

    @GetMapping("/my")
    @Operation(
            summary = "Get my booking history",
            description = "Retrieves all bookings made by the currently authenticated user."
    )
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal User currentUser
    ) {
        List<BookingResponse> bookings = bookingService.getUserBookings(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok("Bookings retrieved successfully", bookings));
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(
            summary = "Cancel booking",
            description = "Cancels a confirmed booking and restores the seat to AVAILABLE."
    )
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal User currentUser
    ) {
        BookingResponse response = bookingService.cancelBooking(bookingId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok("Booking cancelled successfully", response));
    }
}
