package com.miki1smad.ticketresale.seasontickets;

import com.miki1smad.ticketresale.events.Match;
import com.miki1smad.ticketresale.events.MatchRepository;
import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeasonTicketService {

    private final SeasonTicketRepository seasonTicketRepository;
    private final MatchEntitlementRepository matchEntitlementRepository;
    private final MatchRepository matchRepository;
    private final UserService userService;

    @Transactional
    public SeasonTicketResponse claimSeasonTicket(Long userId, String barcode) {
        if (barcode == null || barcode.trim().isEmpty()) {
            throw new IllegalArgumentException("Bar-kod je obavezan");
        }

        String trimmedBarcode = barcode.trim();
        SeasonTicket ticket = seasonTicketRepository.findByBarcode(trimmedBarcode)
                .orElseThrow(() -> new IllegalArgumentException("Nevažeći bar-kod sezonske karte. Molimo proverite unos i pokušajte ponovo."));

        if (ticket.getStatus() != SeasonTicketStatus.UNCLAIMED || ticket.getOwner() != null) {
            throw new IllegalStateException("Sezonska karta sa ovim bar-kodom je već preuzeta.");
        }

        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Korisnik nije pronađen: " + userId));

        ticket.setOwner(user);
        ticket.setStatus(SeasonTicketStatus.ACTIVE);
        ticket.setClaimedAt(Instant.now());
        SeasonTicket savedTicket = seasonTicketRepository.save(ticket);

        List<Match> matches = matchRepository.findBySeasonAndHomeClubId(ticket.getSeason(), ticket.getClub().getId());
        List<MatchEntitlement> entitlements = new ArrayList<>();

        for (Match match : matches) {
            MatchEntitlement entitlement = MatchEntitlement.builder()
                    .seasonTicket(savedTicket)
                    .match(match)
                    .status(EntitlementStatus.OWNER_HELD)
                    .build();
            entitlements.add(matchEntitlementRepository.save(entitlement));
        }

        return SeasonTicketResponse.from(savedTicket, entitlements);
    }

    @Transactional(readOnly = true)
    public List<SeasonTicketResponse> getMySeasonTickets(Long userId) {
        List<SeasonTicket> tickets = seasonTicketRepository.findByOwnerId(userId);
        return tickets.stream().map(ticket -> {
            List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
            return SeasonTicketResponse.from(ticket, entitlements);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableBarcodes() {
        return seasonTicketRepository.findByStatus(SeasonTicketStatus.UNCLAIMED)
                .stream()
                .map(SeasonTicket::getBarcode)
                .toList();
    }
}
