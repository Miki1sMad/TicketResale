package com.miki1smad.ticketresale.orders;

import java.time.Instant;

public record ResaleTicketIssuedEvent(
        Long ticketId,
        Long orderId,
        Long buyerId,
        String buyerEmail,
        String rawBarcode,
        String matchTitle,
        String seatDetails,
        Instant issuedAt) {}
