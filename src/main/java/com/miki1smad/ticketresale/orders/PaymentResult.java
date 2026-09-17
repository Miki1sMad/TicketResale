package com.miki1smad.ticketresale.orders;

public record PaymentResult(boolean successful, String transactionReference, String errorMessage) {

    public static PaymentResult success(String transactionReference) {
        return new PaymentResult(true, transactionReference, null);
    }

    public static PaymentResult failure(String errorMessage) {
        return new PaymentResult(false, null, errorMessage);
    }
}
