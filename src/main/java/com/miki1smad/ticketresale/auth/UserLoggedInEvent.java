package com.miki1smad.ticketresale.auth;

import java.time.Instant;

public record UserLoggedInEvent(Long userId, String email, Instant loggedInAt) {}
