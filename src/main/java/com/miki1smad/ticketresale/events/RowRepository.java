package com.miki1smad.ticketresale.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RowRepository extends JpaRepository<Row, Long> {
    List<Row> findBySectionId(Long sectionId);
}
