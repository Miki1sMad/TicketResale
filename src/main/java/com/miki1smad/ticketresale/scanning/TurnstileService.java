package com.miki1smad.ticketresale.scanning;

import com.miki1smad.ticketresale.events.Match;
import com.miki1smad.ticketresale.events.MatchRepository;
import com.miki1smad.ticketresale.events.Seat;
import com.miki1smad.ticketresale.orders.ResaleTicket;
import com.miki1smad.ticketresale.orders.ResaleTicketRepository;
import com.miki1smad.ticketresale.orders.TicketTokenService;
import com.miki1smad.ticketresale.seasontickets.*;
import com.miki1smad.ticketresale.users.User;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TurnstileService {

    private final MatchRepository matchRepository;
    private final ResaleTicketRepository resaleTicketRepository;
    private final SeasonTicketRepository seasonTicketRepository;
    private final MatchEntitlementRepository matchEntitlementRepository;
    private final TicketTokenService ticketTokenService;
    private final TurnstileAuditService auditService;

    @Transactional
    public TurnstileScanResponse validateEntry(TurnstileScanRequest request, User operator) {
        String turnstileId = request.turnstileId().trim();
        String rawBarcode = request.barcode().trim();

        Match match = matchRepository
                .findById(request.matchId())
                .orElseThrow(() -> new IllegalArgumentException("Utakmica nije pronađena sa ID: " + request.matchId()));

        if (rawBarcode.startsWith("TKT_")) {
            return validateResaleTicket(rawBarcode, turnstileId, match, operator);
        } else {
            return validateSeasonTicket(rawBarcode, turnstileId, match, operator);
        }
    }

    private TurnstileScanResponse validateResaleTicket(
            String rawBarcode, String turnstileId, Match match, User operator) {
        if (!ticketTokenService.validateTokenHmac(rawBarcode)) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    maskBarcode(rawBarcode),
                    null,
                    "RESALE_TICKET",
                    null,
                    null,
                    "INVALID_TOKEN",
                    "Neispravan ili falsifikovan HMAC potpis");
            throw new IllegalArgumentException("Neispravan ili falsifikovan bar-kod");
        }

        String barcodeHash = ticketTokenService.hashToken(rawBarcode);
        Optional<ResaleTicket> ticketOpt = resaleTicketRepository.findByBarcodeHash(barcodeHash);

        if (ticketOpt.isEmpty()) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    maskBarcode(rawBarcode),
                    barcodeHash,
                    "RESALE_TICKET",
                    null,
                    null,
                    "TICKET_NOT_FOUND",
                    "Ulaznica nije pronađena u sistemu");
            throw new IllegalArgumentException("Ulaznica nije pronađena");
        }

        ResaleTicket ticket = ticketOpt.get();

        if (!ticket.getMatch().getId().equals(match.getId())) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    null,
                    barcodeHash,
                    "RESALE_TICKET",
                    ticket,
                    null,
                    "INVALID_MATCH",
                    "Ulaznica je za utakmicu ID: " + ticket.getMatch().getId());
            throw new IllegalArgumentException("Ulaznica nije za ovu utakmicu");
        }

        Seat seat = ticket.getSeat();
        TurnstileScanResponse.SeatInfo seatInfo = new TurnstileScanResponse.SeatInfo(
                seat.getRow().getSection().getStadium().getName(),
                seat.getRow().getSection().getName(),
                seat.getRow().getRowNumber(),
                seat.getSeatNumber());

        Instant now = Instant.now();
        int rowsUpdated = resaleTicketRepository.markAsUsedIfValid(ticket.getId(), turnstileId, now);

        if (rowsUpdated == 0) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    null,
                    barcodeHash,
                    "RESALE_TICKET",
                    ticket,
                    null,
                    "ALREADY_USED",
                    "Ulaznica je već iskorišćena ili nevažeća (status: " + ticket.getStatus() + ")");
            throw new IllegalStateException("Ulaznica je već iskorišćena");
        }

        auditService.recordLog(
                turnstileId, match, operator, null, barcodeHash, "RESALE_TICKET", ticket, null, "GRANTED", null);

        return new TurnstileScanResponse(
                "GRANTED", "Ulaz odobren", match.getId(), turnstileId, "RESALE_TICKET", seatInfo, now);
    }

    private TurnstileScanResponse validateSeasonTicket(
            String rawBarcode, String turnstileId, Match match, User operator) {
        Optional<SeasonTicket> seasonTicketOpt = seasonTicketRepository.findByBarcode(rawBarcode);

        if (seasonTicketOpt.isEmpty()) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    null,
                    "TICKET_NOT_FOUND",
                    "Sezonska karta nije pronađena");
            throw new IllegalArgumentException("Sezonska karta nije pronađena");
        }

        SeasonTicket seasonTicket = seasonTicketOpt.get();

        if (seasonTicket.getStatus() != SeasonTicketStatus.ACTIVE) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    seasonTicket,
                    "INACTIVE_SEASON_TICKET",
                    "Sezonska karta ima status: " + seasonTicket.getStatus());
            throw new IllegalArgumentException("Sezonska karta nije aktivna");
        }

        Optional<MatchEntitlement> entitlementOpt =
                matchEntitlementRepository.findBySeasonTicketIdAndMatchId(seasonTicket.getId(), match.getId());

        if (entitlementOpt.isEmpty()) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    seasonTicket,
                    "INVALID_MATCH",
                    "Sezonska karta ne važi za utakmicu ID: " + match.getId());
            throw new IllegalArgumentException("Sezonska karta ne važi za ovu utakmicu");
        }

        MatchEntitlement entitlement = entitlementOpt.get();
        EntitlementStatus status = entitlement.getStatus();

        if (status == EntitlementStatus.RESOLD) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    seasonTicket,
                    "RESOLD",
                    "Pravo ulaska je preprodato na berzi");
            throw new IllegalStateException("Pravo ulaska sa sezonske karte je preprodato na berzi");
        }

        if (status == EntitlementStatus.LISTED || status == EntitlementStatus.RESERVED) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    seasonTicket,
                    status.name(),
                    "Pravo ulaska je ponuđeno na berzi (status: " + status + ")");
            throw new IllegalStateException("Pravo ulaska sa sezonske karte je trenutno na berzi (" + status + ")");
        }

        if (status == EntitlementStatus.USED) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    seasonTicket,
                    "ALREADY_USED",
                    "Sezonska karta je već iskorišćena za ovu utakmicu");
            throw new IllegalStateException("Sezonska karta je već iskorišćena za ovu utakmicu");
        }

        Seat seat = seasonTicket.getSeat();
        TurnstileScanResponse.SeatInfo seatInfo = new TurnstileScanResponse.SeatInfo(
                seat.getRow().getSection().getStadium().getName(),
                seat.getRow().getSection().getName(),
                seat.getRow().getRowNumber(),
                seat.getSeatNumber());

        int rowsUpdated = matchEntitlementRepository.markAsUsedIfOwnerHeld(entitlement.getId());
        if (rowsUpdated == 0) {
            auditService.recordLog(
                    turnstileId,
                    match,
                    operator,
                    rawBarcode,
                    null,
                    "SEASON_TICKET",
                    null,
                    seasonTicket,
                    "ALREADY_USED",
                    "Paralelno iskorišćavanje sezonske karte");
            throw new IllegalStateException("Sezonska karta je već iskorišćena za ovu utakmicu");
        }

        Instant now = Instant.now();
        auditService.recordLog(
                turnstileId, match, operator, rawBarcode, null, "SEASON_TICKET", null, seasonTicket, "GRANTED", null);

        return new TurnstileScanResponse(
                "GRANTED", "Ulaz odobren", match.getId(), turnstileId, "SEASON_TICKET", seatInfo, now);
    }

    private String maskBarcode(String barcode) {
        if (barcode == null || barcode.length() < 12) {
            return "***";
        }
        return barcode.substring(0, 8) + "..." + barcode.substring(barcode.length() - 4);
    }
}
