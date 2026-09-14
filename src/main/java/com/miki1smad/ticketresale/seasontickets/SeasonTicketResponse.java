package com.miki1smad.ticketresale.seasontickets;

import java.time.Instant;
import java.util.List;

public record SeasonTicketResponse(
        Long id,
        String barcode,
        String clubName,
        String season,
        String sectionName,
        String rowNumber,
        String seatNumber,
        SeasonTicketStatus status,
        Instant claimedAt,
        List<MatchEntitlementResponse> entitlements
) {
    public static SeasonTicketResponse from(SeasonTicket ticket, List<MatchEntitlement> entitlements) {
        List<MatchEntitlementResponse> entitlementResponses = entitlements.stream()
                .map(MatchEntitlementResponse::from)
                .toList();

        return new SeasonTicketResponse(
                ticket.getId(),
                ticket.getBarcode(),
                ticket.getClub().getName(),
                ticket.getSeason(),
                ticket.getSeat().getRow().getSection().getName(),
                ticket.getSeat().getRow().getRowNumber(),
                ticket.getSeat().getSeatNumber(),
                ticket.getStatus(),
                ticket.getClaimedAt(),
                entitlementResponses
        );
    }
}
