package com.miki1smad.ticketresale.orders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResaleTicketRepository extends JpaRepository<ResaleTicket, Long> {

    List<ResaleTicket> findByOrderId(Long orderId);

    List<ResaleTicket> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"match", "seat", "seat.row", "seat.row.section", "seat.row.section.stadium"})
    Optional<ResaleTicket> findByBarcodeHash(String barcodeHash);

    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE ResaleTicket t SET t.status = com.miki1smad.ticketresale.orders.TicketStatus.USED, t.scannedAt = :scannedAt, t.turnstileId = :turnstileId, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :id AND t.status = com.miki1smad.ticketresale.orders.TicketStatus.VALID")
    int markAsUsedIfValid(
            @Param("id") Long id, @Param("turnstileId") String turnstileId, @Param("scannedAt") Instant scannedAt);
}
