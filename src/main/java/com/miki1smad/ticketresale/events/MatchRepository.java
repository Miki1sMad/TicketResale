package com.miki1smad.ticketresale.events;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findBySeasonAndHomeClubId(String season, Long homeClubId);

    List<Match> findByKickoffTimeAfterOrderByKickoffTimeAsc(Instant after);

    List<Match> findAllByOrderByKickoffTimeAsc();
}
