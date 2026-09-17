package com.miki1smad.ticketresale.orders;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResaleTicketRepository extends JpaRepository<ResaleTicket, Long> {

    List<ResaleTicket> findByOrderId(Long orderId);

    List<ResaleTicket> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    Optional<ResaleTicket> findByBarcodeHash(String barcodeHash);
}
