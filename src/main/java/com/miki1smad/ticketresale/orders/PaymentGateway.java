package com.miki1smad.ticketresale.orders;

public interface PaymentGateway {

    PaymentResult processPayment(PaymentRequest request);
}
