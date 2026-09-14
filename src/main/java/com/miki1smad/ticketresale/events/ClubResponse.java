package com.miki1smad.ticketresale.events;

public record ClubResponse(Long id, String name, String city) {
    public static ClubResponse from(Club club) {
        return new ClubResponse(club.getId(), club.getName(), club.getCity());
    }
}
