package com.miki1smad.ticketresale.seasontickets;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchEntitlementRepository extends JpaRepository<MatchEntitlement, Long> {

    @EntityGraph(attributePaths = {"match", "match.homeClub", "match.awayClub", "seasonTicket"})
    List<MatchEntitlement> findBySeasonTicketId(Long seasonTicketId);

    @EntityGraph(attributePaths = {"match", "match.homeClub", "match.awayClub", "seasonTicket"})
    List<MatchEntitlement> findBySeasonTicketOwnerId(Long ownerId);

    @EntityGraph(attributePaths = {"match", "match.homeClub", "match.awayClub", "seasonTicket"})
    Optional<MatchEntitlement> findBySeasonTicketIdAndMatchId(Long seasonTicketId, Long matchId);

    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE MatchEntitlement me SET me.status = com.miki1smad.ticketresale.seasontickets.EntitlementStatus.USED, me.updatedAt = CURRENT_TIMESTAMP WHERE me.id = :id AND me.status = com.miki1smad.ticketresale.seasontickets.EntitlementStatus.OWNER_HELD")
    int markAsUsedIfOwnerHeld(@Param("id") Long id);
}
