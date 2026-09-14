package com.miki1smad.ticketresale.events;

import jakarta.validation.constraints.NotBlank;

public record CreateClubRequest(
        @NotBlank(message = "Club name is required") String name,

        @NotBlank(message = "City is required") String city) {}
