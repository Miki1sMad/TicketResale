package com.miki1smad.ticketresale.orders;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByOrderId(Long orderId);

    List<PaymentTransaction> findByReservationId(Long reservationId);
}
