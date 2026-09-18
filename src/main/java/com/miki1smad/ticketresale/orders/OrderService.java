package com.miki1smad.ticketresale.orders;

import com.miki1smad.ticketresale.common.PaymentFailedException;
import com.miki1smad.ticketresale.events.Match;
import com.miki1smad.ticketresale.events.Seat;
import com.miki1smad.ticketresale.listings.Listing;
import com.miki1smad.ticketresale.listings.ListingRepository;
import com.miki1smad.ticketresale.listings.ListingStatus;
import com.miki1smad.ticketresale.listings.Reservation;
import com.miki1smad.ticketresale.listings.ReservationRepository;
import com.miki1smad.ticketresale.listings.ReservationStatus;
import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.users.Role;
import com.miki1smad.ticketresale.users.User;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final ResaleTicketRepository resaleTicketRepository;
    private final ReservationRepository reservationRepository;
    private final ListingRepository listingRepository;
    private final MatchEntitlementRepository matchEntitlementRepository;
    private final PaymentGateway paymentGateway;
    private final TicketTokenService ticketTokenService;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    @CacheEvict(value = "listings", allEntries = true)
    public OrderResponse checkout(String idempotencyKey, CheckoutRequest request, User buyer) {
        Reservation reservation = reservationRepository
                .findById(request.reservationId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found with ID: " + request.reservationId()));

        Long listingId = reservation.getListing().getId();
        RLock lock = redissonClient.getLock("lock:listing:" + listingId);
        boolean acquired;
        try {
            acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock on listing: " + listingId, e);
        }

        if (!acquired) {
            throw new IllegalStateException("Could not acquire lock for listing: " + listingId);
        }

        try {
            return transactionTemplate.execute(status -> executeCheckout(idempotencyKey, request, buyer));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private OrderResponse executeCheckout(String idempotencyKey, CheckoutRequest request, User buyer) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }

        String normalizedKey = idempotencyKey.trim();
        var existingOrderOpt = orderRepository.findByIdempotencyKey(normalizedKey);
        if (existingOrderOpt.isPresent()) {
            Order existingOrder = existingOrderOpt.get();
            if (!existingOrder.getBuyer().getId().equals(buyer.getId())) {
                throw new IllegalArgumentException("Idempotency key already used by another user");
            }
            if (!existingOrder.getReservation().getId().equals(request.reservationId())) {
                throw new IllegalStateException("Idempotency key already used for a different reservation");
            }
            return OrderResponse.from(existingOrder, null);
        }

        Reservation reservation = reservationRepository
                .findById(request.reservationId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Reservation not found with ID: " + request.reservationId()));

        if (!reservation.getBuyer().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("You do not own this reservation");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException(
                    "Reservation is not available for checkout (status: " + reservation.getStatus() + ")");
        }

        if (reservation.getExpiresAt().isBefore(Instant.now())) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            throw new IllegalStateException("Reservation has expired");
        }

        String cleanCard = request.cardNumber().replaceAll("\\s+", "");
        String cardLast4 = cleanCard.length() >= 4 ? cleanCard.substring(cleanCard.length() - 4) : cleanCard;

        PaymentResult paymentResult = paymentGateway.processPayment(
                new PaymentRequest(request.paymentMethod(), request.cardNumber(), reservation.getReservedPrice()));

        if (!paymentResult.successful()) {
            paymentTransactionService.recordFailedTransaction(
                    reservation,
                    buyer,
                    reservation.getReservedPrice(),
                    request.paymentMethod(),
                    cardLast4,
                    paymentResult.errorMessage());
            throw new PaymentFailedException(paymentResult.errorMessage());
        }

        Listing listing = reservation.getListing();
        listing.setStatus(ListingStatus.SOLD);
        listingRepository.save(listing);

        MatchEntitlement entitlement = listing.getMatchEntitlement();
        entitlement.setStatus(EntitlementStatus.RESOLD);
        matchEntitlementRepository.save(entitlement);

        reservation.setStatus(ReservationStatus.COMPLETED);
        reservationRepository.save(reservation);

        Order order = Order.builder()
                .buyer(buyer)
                .reservation(reservation)
                .totalAmount(reservation.getReservedPrice())
                .status(OrderStatus.COMPLETED)
                .idempotencyKey(normalizedKey)
                .build();
        order = orderRepository.save(order);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .order(order)
                .reservation(reservation)
                .buyer(buyer)
                .amount(reservation.getReservedPrice())
                .paymentMethod(request.paymentMethod())
                .cardLast4(cardLast4)
                .status(PaymentStatus.SUCCESS)
                .build();
        paymentTransactionRepository.save(transaction);

        String rawToken = ticketTokenService.generateToken();
        String barcodeHash = ticketTokenService.hashToken(rawToken);

        Match match = entitlement.getMatch();
        Seat seat = entitlement.getSeasonTicket().getSeat();

        ResaleTicket ticket = ResaleTicket.builder()
                .order(order)
                .buyer(buyer)
                .match(match)
                .seat(seat)
                .barcodeHash(barcodeHash)
                .status(TicketStatus.VALID)
                .build();
        ticket = resaleTicketRepository.save(ticket);
        order.getTickets().add(ticket);

        return OrderResponse.from(order, rawToken);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, User user) {
        Order order = orderRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + id));

        if (!order.getBuyer().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Access denied to order");
        }

        return OrderResponse.from(order, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(User user) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(o -> OrderResponse.from(o, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(User user) {
        return resaleTicketRepository.findByBuyerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(TicketResponse::from)
                .toList();
    }
}
