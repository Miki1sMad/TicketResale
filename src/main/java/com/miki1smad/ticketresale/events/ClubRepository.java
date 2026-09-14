package com.miki1smad.ticketresale.events;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClubRepository extends JpaRepository<Club, Long> {
    Optional<Club> findByName(String name);
}
