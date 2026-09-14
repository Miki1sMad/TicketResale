package com.miki1smad.ticketresale.events;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RowRepository extends JpaRepository<Row, Long> {
    List<Row> findBySectionId(Long sectionId);
}
