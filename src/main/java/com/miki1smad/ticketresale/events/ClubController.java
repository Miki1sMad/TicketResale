package com.miki1smad.ticketresale.events;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<ClubResponse>> listClubs() {
        return ResponseEntity.ok(eventService.listClubs());
    }

    @PostMapping
    public ResponseEntity<ClubResponse> createClub(@Valid @RequestBody CreateClubRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createClub(request));
    }
}
