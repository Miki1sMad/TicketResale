package com.miki1smad.ticketresale.seasontickets;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchEntitlementRepository extends JpaRepository<MatchEntitlement, Long> {

    @EntityGraph(attributePaths = {"match", "match.homeClub", "match.awayClub", "seasonTicket"})
    List<MatchEntitlement> findBySeasonTicketId(Long seasonTicketId);

    @EntityGraph(attributePaths = {"match", "match.homeClub", "match.awayClub", "seasonTicket"})
    List<MatchEntitlement> findBySeasonTicketOwnerId(Long ownerId);

    @EntityGraph(attributePaths = {"match", "match.homeClub", "match.awayClub", "seasonTicket"})
    Optional<MatchEntitlement> findBySeasonTicketIdAndMatchId(Long seasonTicketId, Long matchId);
}
