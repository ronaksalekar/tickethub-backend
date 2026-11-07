package com.tickethub.event.controller;

import com.tickethub.common.dto.ApiResponse;
import com.tickethub.event.dto.EventResponse;
import com.tickethub.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Public event discovery endpoints")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(
            summary = "Get all events (Paginated)",
            description = "Retrieves a paginated and sorted list of upcoming scheduled events."
    )
    public ResponseEntity<ApiResponse<Page<EventResponse>>> getAllEvents(
            @ParameterObject
            @PageableDefault(size = 20, sort = "eventTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<EventResponse> events = eventService.getAllEvents(pageable);
        return ResponseEntity.ok(ApiResponse.ok("Events retrieved successfully", events));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID", description = "Retrieves event details by event ID (cached in Redis).")
    public ResponseEntity<ApiResponse<EventResponse>> getEventById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Event retrieved successfully", eventService.getEventById(id)));
    }
}
