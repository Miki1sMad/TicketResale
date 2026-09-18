package com.miki1smad.ticketresale.listings;

import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.users.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    public static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    private final ListingRepository listingRepository;
    private final ReservationRepository reservationRepository;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    @CacheEvict(value = "listings", allEntries = true)
    public ReservationResponse createReservation(CreateReservationRequest request, User buyer) {
        RLock lock = redissonClient.getLock("lock:listing:" + request.listingId());
        boolean acquired;
        try {
            acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock on listing: " + request.listingId(), e);
        }

        if (!acquired) {
            throw new IllegalStateException("Could not acquire lock for listing: " + request.listingId());
        }

        try {
            return transactionTemplate.execute(status -> executeCreateReservation(request, buyer));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private ReservationResponse executeCreateReservation(CreateReservationRequest request, User buyer) {
        Listing listing = listingRepository
                .findByIdForUpdate(request.listingId())
                .orElseThrow(() -> new IllegalArgumentException("Listing not found with ID: " + request.listingId()));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Listing is not available for reservation (status: " + listing.getStatus() + ")");
        }

        if (listing.getSeller().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("You cannot reserve your own listing");
        }

        if (listing.getMatchEntitlement().getMatch().getKickoffTime().isBefore(Instant.now())) {
            throw new IllegalStateException("Cannot reserve ticket for a match that has already started");
        }

        listing.setStatus(ListingStatus.RESERVED);
        MatchEntitlement entitlement = listing.getMatchEntitlement();
        entitlement.setStatus(EntitlementStatus.RESERVED);
        listingRepository.save(listing);

        Reservation reservation = Reservation.builder()
                .listing(listing)
                .buyer(buyer)
                .reservedPrice(listing.getPrice())
                .status(ReservationStatus.PENDING)
                .expiresAt(Instant.now().plus(RESERVATION_TTL))
                .build();

        reservation = reservationRepository.save(reservation);
        log.info(
                "Reserved listing ID: {} by buyer ID: {}, reservation ID: {}",
                listing.getId(),
                buyer.getId(),
                reservation.getId());
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id, User user) {
        Reservation reservation = reservationRepository
                .findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found with ID: " + id));

        if (!reservation.getBuyer().getId().equals(user.getId())
                && !user.getRole().name().equals("ADMIN")) {
            throw new IllegalArgumentException("Access denied to reservation");
        }

        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(Long buyerId) {
        return reservationRepository.findByBuyerId(buyerId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Scheduled(fixedDelay = 30000)
    @Transactional
    @CacheEvict(value = "listings", allEntries = true)
    public void scheduledExpirePendingReservations() {
        expirePendingReservations(Instant.now());
    }

    @Transactional
    @CacheEvict(value = "listings", allEntries = true)
    public int expirePendingReservations(Instant asOf) {
        List<Reservation> expiredReservations =
                reservationRepository.findByStatusAndExpiresAtLessThanEqual(ReservationStatus.PENDING, asOf);

        for (Reservation reservation : expiredReservations) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            Listing listing = reservation.getListing();
            if (listing.getStatus() == ListingStatus.RESERVED) {
                listing.setStatus(ListingStatus.ACTIVE);
            }
            MatchEntitlement entitlement = listing.getMatchEntitlement();
            if (entitlement.getStatus() == EntitlementStatus.RESERVED) {
                entitlement.setStatus(EntitlementStatus.LISTED);
            }
            log.info("Expired reservation ID: {}, restored listing ID: {}", reservation.getId(), listing.getId());
        }

        return expiredReservations.size();
    }
}
