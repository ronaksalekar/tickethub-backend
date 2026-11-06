package com.tickethub.seat.repository;

import com.tickethub.seat.model.Seat;
import com.tickethub.seat.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventId(Long eventId);
    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);
    Optional<Seat> findByEventIdAndSeatNumber(Long eventId, String seatNumber);
}
