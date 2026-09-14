package com.miki1smad.ticketresale.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateMatchRequest(
        @NotNull(message = "Home club ID is required")
        Long homeClubId,

        @NotNull(message = "Away club ID is required")
        Long awayClubId,

        @NotNull(message = "Stadium ID is required")
        Long stadiumId,

        @NotNull(message = "Kickoff time is required")
        Instant kickoffTime,

        @NotBlank(message = "Season is required")
        String season
) {}
