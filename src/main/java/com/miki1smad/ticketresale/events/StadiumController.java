package com.miki1smad.ticketresale.events;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stadiums")
@RequiredArgsConstructor
public class StadiumController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<StadiumResponse>> listStadiums() {
        return ResponseEntity.ok(eventService.listStadiums());
    }

    @PostMapping
    public ResponseEntity<StadiumResponse> createStadium(@Valid @RequestBody CreateStadiumRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createStadium(request));
    }
}
