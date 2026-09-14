package com.miki1smad.ticketresale.events;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getUpcomingMatches() {
        return ResponseEntity.ok(eventService.listUpcomingMatches());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatchById(@PathVariable Long id) {
        return ResponseEntity.ok(MatchResponse.from(eventService.getMatchEntity(id)));
    }

    @PostMapping
    public ResponseEntity<MatchResponse> createMatch(@Valid @RequestBody CreateMatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createMatch(request));
    }
}
