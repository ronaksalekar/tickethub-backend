package com.tickethub.event.service;

import com.tickethub.common.exception.ResourceNotFoundException;
import com.tickethub.event.dto.CreateEventRequest;
import com.tickethub.event.dto.CreateVenueRequest;
import com.tickethub.event.dto.EventResponse;
import com.tickethub.event.model.Event;
import com.tickethub.event.model.Venue;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.VenueRepository;
import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import com.tickethub.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public Venue createVenue(CreateVenueRequest request) {
        Venue venue = Venue.builder()
                .name(request.getName())
                .city(request.getCity())
                .address(request.getAddress())
                .totalCapacity(request.getTotalCapacity())
                .build();
        return venueRepository.save(venue);
    }

    @Transactional
    @CacheEvict(value = {"events", "event_list"}, allEntries = true)
    public EventResponse createEvent(CreateEventRequest request) {
        Venue venue = venueRepository.findById(request.getVenueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found with id: " + request.getVenueId()));

        Event event = Event.builder()
                .venue(venue)
                .title(request.getTitle())
                .description(request.getDescription())
                .eventTime(request.getEventTime())
                .totalSeats(request.getTotalSeats())
                .build();

        event = eventRepository.save(event);

        // Pre-populate seat inventory for this event
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= request.getTotalSeats(); i++) {
            seats.add(Seat.builder()
                    .event(event)
                    .seatNumber("S-" + i)
                    .price(request.getDefaultSeatPrice())
                    .status(SeatStatus.AVAILABLE)
                    .version(0L)
                    .build());
        }
        seatRepository.saveAll(seats);

        return EventResponse.fromEntity(event);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "event_list", key = "'all'")
    public List<EventResponse> getAllEvents() {
        return eventRepository.findAll()
                .stream()
                .map(EventResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "events", key = "#eventId")
    public EventResponse getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        return EventResponse.fromEntity(event);
    }
}
