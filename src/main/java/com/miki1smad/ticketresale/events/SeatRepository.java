package com.miki1smad.ticketresale.events;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByRowId(Long rowId);
}
