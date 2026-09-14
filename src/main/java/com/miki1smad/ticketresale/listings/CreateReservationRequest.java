package com.miki1smad.ticketresale.listings;

import jakarta.validation.constraints.NotNull;

public record CreateReservationRequest(
        @NotNull(message = "Listing ID is required") Long listingId) {}
