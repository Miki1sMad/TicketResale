package com.miki1smad.ticketresale.orders;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        String cleanCard = request.cardNumber() != null ? request.cardNumber().replaceAll("\\s+", "") : "";

        if (cleanCard.endsWith("0000")) {
            return PaymentResult.failure("Card payment was declined by issuing bank (insufficient funds / test card)");
        }

        String transactionRef = "TXN-" + UUID.randomUUID();
        return PaymentResult.success(transactionRef);
    }
}
