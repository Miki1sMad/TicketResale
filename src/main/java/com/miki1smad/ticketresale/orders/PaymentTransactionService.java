package com.miki1smad.ticketresale.orders;

import com.miki1smad.ticketresale.listings.Reservation;
import com.miki1smad.ticketresale.users.User;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedTransaction(
            Reservation reservation,
            User buyer,
            BigDecimal amount,
            String paymentMethod,
            String cardLast4,
            String failureReason) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .reservation(reservation)
                .buyer(buyer)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .cardLast4(cardLast4)
                .status(PaymentStatus.FAILED)
                .failureReason(failureReason)
                .build();
        paymentTransactionRepository.save(transaction);
    }
}
