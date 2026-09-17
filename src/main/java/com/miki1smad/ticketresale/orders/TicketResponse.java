package com.miki1smad.ticketresale.orders;

import java.time.Instant;

public record TicketResponse(
        Long id,
        Long matchId,
        String homeClubName,
        String awayClubName,
        Instant kickoffTime,
        String stadiumName,
        String sectionName,
        String rowNumber,
        String seatNumber,
        TicketStatus status,
        Instant createdAt) {

    public static TicketResponse from(ResaleTicket ticket) {
        var match = ticket.getMatch();
        var seat = ticket.getSeat();
        var row = seat.getRow();
        var section = row.getSection();
        var stadium = section.getStadium();

        return new TicketResponse(
                ticket.getId(),
                match.getId(),
                match.getHomeClub().getName(),
                match.getAwayClub().getName(),
                match.getKickoffTime(),
                stadium.getName(),
                section.getName(),
                row.getRowNumber(),
                seat.getSeatNumber(),
                ticket.getStatus(),
                ticket.getCreatedAt());
    }
}
