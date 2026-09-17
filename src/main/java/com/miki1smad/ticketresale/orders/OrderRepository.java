package com.miki1smad.ticketresale.orders;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    Optional<Order> findByReservationId(Long reservationId);
}
