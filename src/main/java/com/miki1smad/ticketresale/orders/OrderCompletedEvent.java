package com.miki1smad.ticketresale.orders;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCompletedEvent(
        Long orderId,
        Long buyerId,
        String buyerEmail,
        Long sellerId,
        String sellerEmail,
        String matchTitle,
        BigDecimal price,
        Instant purchasedAt) {}
