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
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody CreateListingRequest request, Authentication authentication) {
        User user = getUser(authentication);
        ListingResponse response = listingService.createListing(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ListingResponse>> getActiveListings(@RequestParam(required = false) Long matchId) {
        return ResponseEntity.ok(listingService.getActiveListings(matchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getListingById(@PathVariable Long id) {
        return ResponseEntity.ok(listingService.getListingById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ListingResponse> cancelListing(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(listingService.cancelListing(id, user));
    }

    private User getUser(Authentication authentication) {
        return userService
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authentication.getName()));
    }
}
