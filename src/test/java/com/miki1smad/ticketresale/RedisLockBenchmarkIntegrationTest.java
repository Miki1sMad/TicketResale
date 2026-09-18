package com.miki1smad.ticketresale;

import static org.assertj.core.api.Assertions.assertThat;

import com.miki1smad.ticketresale.listings.CreateReservationRequest;
import com.miki1smad.ticketresale.listings.Listing;
import com.miki1smad.ticketresale.listings.ListingRepository;
import com.miki1smad.ticketresale.listings.ListingStatus;
import com.miki1smad.ticketresale.listings.Reservation;
import com.miki1smad.ticketresale.listings.ReservationRepository;
import com.miki1smad.ticketresale.listings.ReservationService;
import com.miki1smad.ticketresale.listings.ReservationStatus;
import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketService;
import com.miki1smad.ticketresale.users.Role;
import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=50")
class RedisLockBenchmarkIntegrationTest extends BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RedisLockBenchmarkIntegrationTest.class);

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MatchEntitlementRepository matchEntitlementRepository;

    @Autowired
    private SeasonTicketRepository seasonTicketRepository;

    @Autowired
    private SeasonTicketService seasonTicketService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    record BenchmarkResult(
            String strategy,
            int totalRequests,
            int successfulRequests,
            int conflictRequests,
            long elapsedMillis,
            double throughputOpsPerSec,
            double avgLatencyMs) {}

    @Test
    @DisplayName("Performance Benchmark: Redisson Distributed Lock vs PostgreSQL Pessimistic Lock")
    void benchmarkRedisLockVsPostgresPessimisticLock() throws Exception {
        int concurrency = 20;

        // Setup test buyers
        String encodedPassword = passwordEncoder.encode("Password123!");
        List<User> buyers = new ArrayList<>(concurrency);
        for (int i = 1; i <= concurrency; i++) {
            buyers.add(User.builder()
                    .email("bench_buyer_" + i + "_" + System.currentTimeMillis() + "@example.com")
                    .password(encodedPassword)
                    .firstName("Bench")
                    .lastName("Buyer" + i)
                    .role(Role.USER)
                    .build());
        }
        buyers = userRepository.saveAll(buyers);

        // 1. Setup Listing for Strategy 1: Redis + DB Pessimistic Lock (Defense-in-depth)
        Listing listingStrategyA = createActiveListing("ST-2025-007", "seller_a_" + System.currentTimeMillis());

        // 2. Setup Listing for Strategy 2: Pure DB Pessimistic Lock
        Listing listingStrategyB = createActiveListing("ST-2025-008", "seller_b_" + System.currentTimeMillis());

        // Run Benchmark for Strategy A: Redis + DB Lock (ReservationService)
        BenchmarkResult resultA = runBenchmark(
                "Redis Lock + DB Pessimistic Lock", buyers, listingStrategyA.getId(), (buyer, listingId) -> {
                    try {
                        reservationService.createReservation(new CreateReservationRequest(listingId), buyer);
                        return true;
                    } catch (IllegalStateException e) {
                        return false;
                    }
                });

        // Run Benchmark for Strategy B: Pure DB Pessimistic Lock (Direct Transactional DB)
        BenchmarkResult resultB = runBenchmark(
                "Pure PostgreSQL Pessimistic Lock", buyers, listingStrategyB.getId(), (buyer, listingId) -> {
                    try {
                        return transactionTemplate.execute(status -> {
                            Listing listing = listingRepository
                                    .findByIdForUpdate(listingId)
                                    .orElseThrow();
                            if (listing.getStatus() != ListingStatus.ACTIVE) {
                                return false;
                            }
                            listing.setStatus(ListingStatus.RESERVED);
                            listing.getMatchEntitlement().setStatus(EntitlementStatus.RESERVED);
                            listingRepository.save(listing);

                            reservationRepository.save(Reservation.builder()
                                    .listing(listing)
                                    .buyer(buyer)
                                    .reservedPrice(listing.getPrice())
                                    .status(ReservationStatus.PENDING)
                                    .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                                    .build());
                            return true;
                        });
                    } catch (Exception e) {
                        return false;
                    }
                });

        // Log results
        log.info("==================================================================================");
        log.info("                 CONCURRENCY LOCK BENCHMARK REPORT                                ");
        log.info("==================================================================================");
        log.info(String.format(
                "%-35s | Total: %d | Success: %d | Fail: %d | Time: %d ms | Throughput: %.1f ops/s | Avg: %.2f ms",
                resultA.strategy(),
                resultA.totalRequests(),
                resultA.successfulRequests(),
                resultA.conflictRequests(),
                resultA.elapsedMillis(),
                resultA.throughputOpsPerSec(),
                resultA.avgLatencyMs()));
        log.info(String.format(
                "%-35s | Total: %d | Success: %d | Fail: %d | Time: %d ms | Throughput: %.1f ops/s | Avg: %.2f ms",
                resultB.strategy(),
                resultB.totalRequests(),
                resultB.successfulRequests(),
                resultB.conflictRequests(),
                resultB.elapsedMillis(),
                resultB.throughputOpsPerSec(),
                resultB.avgLatencyMs()));
        log.info("==================================================================================");

        // Verifications:
        // Both strategies MUST guarantee exactly 1 winner, no double-reservation
        assertThat(resultA.successfulRequests()).isEqualTo(1);
        assertThat(resultA.conflictRequests()).isEqualTo(concurrency - 1);

        assertThat(resultB.successfulRequests()).isEqualTo(1);
        assertThat(resultB.conflictRequests()).isEqualTo(concurrency - 1);
    }

    @FunctionalInterface
    interface ReservationAction {
        boolean execute(User buyer, Long listingId);
    }

    private BenchmarkResult runBenchmark(String name, List<User> buyers, Long listingId, ReservationAction action)
            throws InterruptedException {
        int count = buyers.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(count);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (User buyer : buyers) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean success = action.execute(buyer, listingId);
                    if (success) {
                        successCount.incrementAndGet();
                    } else {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.nanoTime();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        long elapsedNanos = System.nanoTime() - start;
        executor.shutdown();

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (elapsedMillis == 0) {
            elapsedMillis = 1;
        }
        double throughput = (count * 1000.0) / elapsedMillis;
        double avgLatency = (double) elapsedMillis / count;

        return new BenchmarkResult(
                name, count, successCount.get(), conflictCount.get(), elapsedMillis, throughput, avgLatency);
    }

    private Listing createActiveListing(String barcode, String sellerEmailPrefix) {
        User seller = userRepository.save(User.builder()
                .email(sellerEmailPrefix + "@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Seller")
                .lastName("Bench")
                .role(Role.USER)
                .build());

        seasonTicketService.claimSeasonTicket(seller.getId(), barcode);
        SeasonTicket ticket = seasonTicketRepository.findByBarcode(barcode).orElseThrow();

        MatchEntitlement entitlement =
                matchEntitlementRepository.findBySeasonTicketId(ticket.getId()).getFirst();
        entitlement.setStatus(EntitlementStatus.LISTED);
        matchEntitlementRepository.save(entitlement);

        return listingRepository.save(Listing.builder()
                .seller(seller)
                .matchEntitlement(entitlement)
                .price(new BigDecimal("5000.00"))
                .status(ListingStatus.ACTIVE)
                .build());
    }
}
