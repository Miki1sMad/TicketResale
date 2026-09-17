package com.miki1smad.ticketresale.orders;

import java.math.BigDecimal;

public record PaymentRequest(String paymentMethod, String cardNumber, BigDecimal amount) {}
