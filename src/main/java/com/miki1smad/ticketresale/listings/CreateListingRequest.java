package com.miki1smad.ticketresale.listings;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateListingRequest(
        @NotNull(message = "Match entitlement ID is required")
        Long matchEntitlementId,

        @NotNull(message = "Price is required") @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price) {}
