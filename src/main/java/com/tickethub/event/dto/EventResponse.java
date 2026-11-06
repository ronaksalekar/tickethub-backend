package com.tickethub.event.dto;

import com.tickethub.event.model.Event;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {
    private Long id;
    private Long venueId;
    private String venueName;
    private String venueCity;
    private String title;
    private String description;
    private Instant eventTime;
    private Integer totalSeats;

    public static EventResponse fromEntity(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .venueId(event.getVenue().getId())
                .venueName(event.getVenue().getName())
                .venueCity(event.getVenue().getCity())
                .title(event.getTitle())
                .description(event.getDescription())
                .eventTime(event.getEventTime())
                .totalSeats(event.getTotalSeats())
                .build();
    }
}
