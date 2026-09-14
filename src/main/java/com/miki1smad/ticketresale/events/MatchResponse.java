package com.miki1smad.ticketresale.events;

import java.time.Instant;

public record MatchResponse(
        Long id,
        Long homeClubId,
        String homeClubName,
        Long awayClubId,
        String awayClubName,
        Long stadiumId,
        String stadiumName,
        Instant kickoffTime,
        String season,
        MatchStatus status) {
    public static MatchResponse from(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getHomeClub().getId(),
                match.getHomeClub().getName(),
                match.getAwayClub().getId(),
                match.getAwayClub().getName(),
                match.getStadium().getId(),
                match.getStadium().getName(),
                match.getKickoffTime(),
                match.getSeason(),
                match.getStatus());
    }
}
