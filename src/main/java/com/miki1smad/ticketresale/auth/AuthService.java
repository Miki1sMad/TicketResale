package com.miki1smad.ticketresale.auth;

import com.miki1smad.ticketresale.seasontickets.SeasonTicketService;
import com.miki1smad.ticketresale.users.Role;
import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SeasonTicketService seasonTicketService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        User user = userService.registerUser(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                Role.USER
        );

        if (request.seasonTicketBarcode() != null && !request.seasonTicketBarcode().trim().isEmpty()) {
            seasonTicketService.claimSeasonTicket(user.getId(), request.seasonTicketBarcode().trim());
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        return AuthResponse.of(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userService.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        return AuthResponse.of(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        String email = jwtService.extractUsername(refreshToken);

        if (email == null || !jwtService.isTokenValid(refreshToken, email)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String tokenType = jwtService.extractAllClaims(refreshToken).get("type", String.class);
        if (!"REFRESH".equals(tokenType)) {
            throw new BadCredentialsException("Token is not a refresh token");
        }

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.of(newAccessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }
}
