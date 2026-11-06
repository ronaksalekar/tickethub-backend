package com.tickethub.seat.service;

import com.tickethub.common.exception.BadRequestException;
import com.tickethub.common.exception.ResourceNotFoundException;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.seat.dto.SeatHoldResponse;
import com.tickethub.seat.dto.SeatResponse;
import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import com.tickethub.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final SeatHoldService seatHoldService;

    /**
     * Aggregates persistent PostgreSQL seat records with real-time Redis in-memory hold state.
     */
    @Transactional(readOnly = true)
    public List<SeatResponse> getSeatsForEvent(Long eventId, Long currentUserId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }

        List<Seat> seats = seatRepository.findByEventId(eventId);

        return seats.stream().map(seat -> {
            String effectiveStatus;
            boolean heldByCurrentUser = false;
            Long remainingTtl = 0L;

            if (seat.getStatus() == SeatStatus.BOOKED) {
                effectiveStatus = SeatStatus.BOOKED.name();
            } else {
                Optional<Long> holdOwner = seatHoldService.getHoldOwner(eventId, seat.getId());
                if (holdOwner.isPresent()) {
                    effectiveStatus = SeatStatus.HELD.name();
                    remainingTtl = seatHoldService.getRemainingTtl(eventId, seat.getId());
                    if (currentUserId != null && currentUserId.equals(holdOwner.get())) {
                        heldByCurrentUser = true;
                    }
                } else {
                    effectiveStatus = SeatStatus.AVAILABLE.name();
                }
            }

            return SeatResponse.builder()
                    .id(seat.getId())
                    .eventId(eventId)
                    .seatNumber(seat.getSeatNumber())
                    .price(seat.getPrice())
                    .status(effectiveStatus)
                    .heldByCurrentUser(heldByCurrentUser)
                    .holdRemainingTtlSeconds(remainingTtl)
                    .build();
        }).toList();
    }

    /**
     * Places a 5-minute hold on a seat if it is available in DB and not currently held in Redis.
     */
    @Transactional(readOnly = true)
    public SeatHoldResponse holdSeat(Long eventId, Long seatId, Long userId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + seatId));

        if (!seat.getEvent().getId().equals(eventId)) {
            throw new BadRequestException("Seat " + seatId + " does not belong to event " + eventId);
        }

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new BadRequestException("Seat " + seat.getSeatNumber() + " is already permanently booked.");
        }

        return seatHoldService.holdSeat(eventId, seatId, userId);
    }

    /**
     * Releases a temporary hold held by the specified user.
     */
    public void releaseHold(Long eventId, Long seatId, Long userId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with id: " + seatId));

        if (!seat.getEvent().getId().equals(eventId)) {
            throw new BadRequestException("Seat " + seatId + " does not belong to event " + eventId);
        }

        seatHoldService.releaseHold(eventId, seatId, userId);
    }
}
