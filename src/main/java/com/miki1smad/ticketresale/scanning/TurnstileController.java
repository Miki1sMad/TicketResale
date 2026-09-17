package com.miki1smad.ticketresale.scanning;

import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/turnstile")
@RequiredArgsConstructor
public class TurnstileController {

    private final TurnstileService turnstileService;
    private final UserService userService;

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('STADIUM_OPERATOR', 'ADMIN')")
    public ResponseEntity<TurnstileScanResponse> validate(
            @Valid @RequestBody TurnstileScanRequest request, Principal principal) {
        User operator =
                principal != null ? userService.findByEmail(principal.getName()).orElse(null) : null;
        TurnstileScanResponse response = turnstileService.validateEntry(request, operator);
        return ResponseEntity.ok(response);
    }
}
