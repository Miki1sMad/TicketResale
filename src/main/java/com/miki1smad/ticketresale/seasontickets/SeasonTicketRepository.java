package com.miki1smad.ticketresale.seasontickets;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonTicketRepository extends JpaRepository<SeasonTicket, Long> {

    @EntityGraph(attributePaths = {"owner", "club", "seat", "seat.row", "seat.row.section", "seat.row.section.stadium"})
    Optional<SeasonTicket> findByBarcode(String barcode);

    @EntityGraph(attributePaths = {"owner", "club", "seat", "seat.row", "seat.row.section", "seat.row.section.stadium"})
    List<SeasonTicket> findByOwnerId(Long ownerId);

    List<SeasonTicket> findByStatus(SeasonTicketStatus status);
}
