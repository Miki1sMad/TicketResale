package com.miki1smad.ticketresale.listings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByStatusAndExpiresAtLessThanEqual(ReservationStatus status, Instant now);

    List<Reservation> findByBuyerId(Long buyerId);

    Optional<Reservation> findByListingIdAndStatus(Long listingId, ReservationStatus status);
}
