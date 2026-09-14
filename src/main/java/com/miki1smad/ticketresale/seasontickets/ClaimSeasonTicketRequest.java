package com.miki1smad.ticketresale.seasontickets;

import jakarta.validation.constraints.NotBlank;

public record ClaimSeasonTicketRequest(
        @NotBlank(message = "Bar-kod je obavezan")
        String barcode
) {}
