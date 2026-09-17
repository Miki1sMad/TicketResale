package com.miki1smad.ticketresale.scanning;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnstileScanLogRepository extends JpaRepository<TurnstileScanLog, Long> {

    List<TurnstileScanLog> findByTurnstileId(String turnstileId);

    List<TurnstileScanLog> findByMatchId(Long matchId);
}
