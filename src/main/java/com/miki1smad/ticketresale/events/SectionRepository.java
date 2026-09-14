package com.miki1smad.ticketresale.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByStadiumId(Long stadiumId);
}
