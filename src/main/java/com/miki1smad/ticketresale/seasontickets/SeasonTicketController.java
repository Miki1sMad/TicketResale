package com.miki1smad.ticketresale.seasontickets;

import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/season-tickets")
@RequiredArgsConstructor
public class SeasonTicketController {

    private final SeasonTicketService seasonTicketService;
    private final UserService userService;

    @PostMapping("/claim")
    public ResponseEntity<SeasonTicketResponse> claimSeasonTicket(
            @Valid @RequestBody ClaimSeasonTicketRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Korisnik nije pronađen"));

        SeasonTicketResponse response = seasonTicketService.claimSeasonTicket(user.getId(), request.barcode());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<SeasonTicketResponse>> getMySeasonTickets(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Korisnik nije pronađen"));

        return ResponseEntity.ok(seasonTicketService.getMySeasonTickets(user.getId()));
    }

    @GetMapping("/available-barcodes")
    public ResponseEntity<List<String>> getAvailableBarcodes() {
        return ResponseEntity.ok(seasonTicketService.getAvailableBarcodes());
    }
}
