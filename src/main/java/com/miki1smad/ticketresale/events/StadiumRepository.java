package com.miki1smad.ticketresale.events;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {
    List<Stadium> findByClubId(Long clubId);
}
