package com.miki1smad.ticketresale.listings;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long listingId,
        Long buyerId,
        String buyerEmail,
        BigDecimal reservedPrice,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getListing().getId(),
                reservation.getBuyer().getId(),
                reservation.getBuyer().getEmail(),
                reservation.getReservedPrice(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt());
    }
}
