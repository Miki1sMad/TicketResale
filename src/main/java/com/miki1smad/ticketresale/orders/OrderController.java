package com.miki1smad.ticketresale.orders;

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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;

    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        OrderResponse response = orderService.checkout(idempotencyKey, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(orderService.getOrderById(id, user));
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> getMyOrders(Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(orderService.getMyOrders(user));
    }

    @GetMapping("/my-tickets")
    public ResponseEntity<List<TicketResponse>> getMyTickets(Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(orderService.getMyTickets(user));
    }

    private User getUser(Authentication authentication) {
        return userService
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + authentication.getName()));
    }
}
