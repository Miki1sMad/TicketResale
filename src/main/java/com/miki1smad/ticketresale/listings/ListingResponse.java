package com.miki1smad.ticketresale.listings;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingResponse(
        Long id,
        Long matchEntitlementId,
        Long matchId,
        String homeClubName,
        String awayClubName,
        Instant kickoffTime,
        String stadiumName,
        String sectionName,
        String rowNumber,
        String seatNumber,
        BigDecimal price,
        ListingStatus status,
        Long sellerId,
        String sellerEmail,
        Instant createdAt) {

    public static ListingResponse from(Listing listing) {
        var entitlement = listing.getMatchEntitlement();
        var match = entitlement.getMatch();
        var seat = entitlement.getSeasonTicket().getSeat();
        var row = seat.getRow();
        var section = row.getSection();
        var stadium = section.getStadium();

        return new ListingResponse(
                listing.getId(),
                entitlement.getId(),
                match.getId(),
                match.getHomeClub().getName(),
                match.getAwayClub().getName(),
                match.getKickoffTime(),
                stadium.getName(),
                section.getName(),
                row.getRowNumber(),
                seat.getSeatNumber(),
                listing.getPrice(),
                listing.getStatus(),
                listing.getSeller().getId(),
                listing.getSeller().getEmail(),
                listing.getCreatedAt());
    }
}
