package com.miki1smad.ticketresale.scanning;

import java.time.Instant;

public record TurnstileScanResponse(
        String result,
        String message,
        Long matchId,
        String turnstileId,
        String ticketType,
        SeatInfo seatInfo,
        Instant scannedAt) {

    public record SeatInfo(String stadiumName, String sectionName, String rowNumber, String seatNumber) {}
}
