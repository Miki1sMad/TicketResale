package com.miki1smad.ticketresale.auth;

import java.time.Instant;

public record UserRegisteredEvent(Long userId, String email, String firstName, String lastName, Instant registeredAt) {}
