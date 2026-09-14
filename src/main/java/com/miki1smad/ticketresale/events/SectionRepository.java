package com.miki1smad.ticketresale.events;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByStadiumId(Long stadiumId);
}
