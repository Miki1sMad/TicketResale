package com.miki1smad.ticketresale.events;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateStadiumRequest(
        Long clubId,

        @NotBlank(message = "Stadium name is required")
        String name,

        @NotBlank(message = "City is required")
        String city,

        @Min(value = 1, message = "Capacity must be greater than zero")
        int capacity
) {}
