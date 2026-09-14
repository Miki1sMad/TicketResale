package com.miki1smad.ticketresale.listings;

import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request, Authentication authentication) {
        User user = getUser(authentication);
        ReservationResponse response = reservationService.createReservation(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getReservationById(
            @PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(reservationService.getReservationById(id, user));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(reservationService.getMyReservations(user.getId()));
    }

    private User getUser(Authentication authentication) {
        return userService
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authentication.getName()));
    }
}
