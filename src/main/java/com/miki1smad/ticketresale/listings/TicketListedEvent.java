package com.miki1smad.ticketresale.listings;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketListedEvent(
        Long listingId, Long sellerId, String sellerEmail, String matchTitle, BigDecimal price, Instant listedAt) {}
