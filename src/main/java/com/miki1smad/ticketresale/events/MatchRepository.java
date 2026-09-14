package com.miki1smad.ticketresale.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findBySeasonAndHomeClubId(String season, Long homeClubId);
    List<Match> findByKickoffTimeAfterOrderByKickoffTimeAsc(Instant after);
    List<Match> findAllByOrderByKickoffTimeAsc();
}
