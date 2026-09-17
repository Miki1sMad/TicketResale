package com.miki1smad.ticketresale.scanning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TurnstileScanRequest(
        @NotBlank(message = "Barcode is required") String barcode,
        @NotBlank(message = "Turnstile ID is required") String turnstileId,
        @NotNull(message = "Match ID is required") Long matchId) {}
