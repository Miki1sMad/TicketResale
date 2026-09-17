package com.miki1smad.ticketresale.orders;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(
        @NotNull(message = "Reservation ID is required") Long reservationId,

        @NotBlank(message = "Payment method is required") String paymentMethod,

        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^[0-9]{13,19}$", message = "Card number must contain between 13 and 19 digits")
        String cardNumber) {}
