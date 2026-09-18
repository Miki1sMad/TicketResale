package com.miki1smad.ticketresale.seasontickets;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonTicketRepository extends JpaRepository<SeasonTicket, Long> {

    @EntityGraph(attributePaths = {"owner", "club", "seat", "seat.row", "seat.row.section", "seat.row.section.stadium"})
    Optional<SeasonTicket> findByBarcode(String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"owner", "club", "seat", "seat.row", "seat.row.section", "seat.row.section.stadium"})
    @Query("SELECT st FROM SeasonTicket st WHERE st.barcode = :barcode")
    Optional<SeasonTicket> findByBarcodeForUpdate(@Param("barcode") String barcode);

    @EntityGraph(attributePaths = {"owner", "club", "seat", "seat.row", "seat.row.section", "seat.row.section.stadium"})
    List<SeasonTicket> findByOwnerId(Long ownerId);

    List<SeasonTicket> findByStatus(SeasonTicketStatus status);
}
