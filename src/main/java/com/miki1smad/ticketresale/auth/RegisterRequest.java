package com.miki1smad.ticketresale.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must have at least 6 characters")
        String password,

        @NotBlank(message = "First name is required") String firstName,

        @NotBlank(message = "Last name is required") String lastName,

        String seasonTicketBarcode) {
    public RegisterRequest(String email, String password, String firstName, String lastName) {
        this(email, password, firstName, lastName, null);
    }
}
