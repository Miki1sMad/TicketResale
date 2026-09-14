package com.miki1smad.ticketresale.events;

public record StadiumResponse(
        Long id,
        Long clubId,
        String name,
        String city,
        int capacity
) {
    public static StadiumResponse from(Stadium stadium) {
        return new StadiumResponse(
                stadium.getId(),
                stadium.getClub() != null ? stadium.getClub().getId() : null,
                stadium.getName(),
                stadium.getCity(),
                stadium.getCapacity()
        );
    }
}
