package com.miki1smad.ticketresale.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {
    List<Stadium> findByClubId(Long clubId);
}
