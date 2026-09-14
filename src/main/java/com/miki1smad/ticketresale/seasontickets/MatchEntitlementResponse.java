package com.miki1smad.ticketresale.seasontickets;

import java.time.Instant;

public record MatchEntitlementResponse(
        Long id,
        Long matchId,
        String homeClubName,
        String awayClubName,
        Instant kickoffTime,
        EntitlementStatus status) {
    public static MatchEntitlementResponse from(MatchEntitlement entitlement) {
        return new MatchEntitlementResponse(
                entitlement.getId(),
                entitlement.getMatch().getId(),
                entitlement.getMatch().getHomeClub().getName(),
                entitlement.getMatch().getAwayClub().getName(),
                entitlement.getMatch().getKickoffTime(),
                entitlement.getStatus());
    }
}
