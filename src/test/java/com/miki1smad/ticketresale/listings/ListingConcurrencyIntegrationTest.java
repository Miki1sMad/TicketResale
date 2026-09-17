package com.miki1smad.ticketresale.listings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.miki1smad.ticketresale.auth.JwtService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=50")
@AutoConfigureMockMvc
class ListingConcurrencyIntegrationTest extends com.miki1smad.ticketresale.BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonTicketRepository seasonTicketRepository;

    @Autowired
    private MatchEntitlementRepository matchEntitlementRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SeasonTicketService seasonTicketService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldHandle50ConcurrentReservationsWithExactlyOneSuccessAnd49Conflicts() throws Exception {
        int numberOfThreads = 50;

        // 1. Create seller and claim season ticket ST-2025-004
        User seller = userRepository.save(User.builder()
                .email("seller_stress@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .firstName("Seller")
                .lastName("Stress")
                .role(Role.USER)
                .build());

        seasonTicketService.claimSeasonTicket(seller.getId(), "ST-2025-004");
        SeasonTicket ticket =
                seasonTicketRepository.findByBarcode("ST-2025-004").orElseThrow();

        List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
        MatchEntitlement entitlement = entitlements.getFirst();
        entitlement.setStatus(EntitlementStatus.LISTED);
        matchEntitlementRepository.save(entitlement);

        // 2. Create an ACTIVE listing
        Listing listing = listingRepository.save(Listing.builder()
                .seller(seller)
                .matchEntitlement(entitlement)
                .price(new BigDecimal("4500.00"))
                .status(ListingStatus.ACTIVE)
                .build());

        Long listingId = listing.getId();

        // 3. Pre-create 50 buyer users and generate JWT tokens
        String encodedPassword = passwordEncoder.encode("Password123!");
        List<User> buyers = new ArrayList<>(numberOfThreads);
        for (int i = 1; i <= numberOfThreads; i++) {
            buyers.add(User.builder()
                    .email("concurrent_buyer_" + i + "@example.com")
                    .password(encodedPassword)
                    .firstName("Buyer")
                    .lastName("No" + i)
                    .role(Role.USER)
                    .build());
        }
        buyers = userRepository.saveAll(buyers);

        List<String> buyerTokens = new ArrayList<>(numberOfThreads);
        for (User buyer : buyers) {
            buyerTokens.add(jwtService.generateAccessToken(
                    buyer.getId(), buyer.getEmail(), buyer.getRole().name()));
        }

        // 4. Setup concurrent burst execution
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        String reservePayload = String.format("{\"listingId\": %d}", listingId);

        for (int i = 0; i < numberOfThreads; i++) {
            final String token = buyerTokens.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for gun shot

                    MvcResult result = mockMvc.perform(post("/api/v1/reservations")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(reservePayload))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 5. Fire all 50 threads simultaneously
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // 6. Verifications as required by DoD:
        // Exactly 1 thread got HTTP 201, and exactly 49 got HTTP 409 Conflict
        assertThat(successCount.get())
                .as("Exactly 1 reservation request must succeed")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("Exactly 49 reservation requests must fail with 409 Conflict")
                .isEqualTo(numberOfThreads - 1);
        assertThat(otherCount.get())
                .as("No other HTTP statuses or errors should occur")
                .isEqualTo(0);

        // Verify database state: exactly 1 PENDING reservation for this listing
        List<Reservation> activeReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getListing().getId().equals(listingId) && r.getStatus() == ReservationStatus.PENDING)
                .toList();

        assertThat(activeReservations).hasSize(1);

        Listing finalListing = listingRepository.findById(listingId).orElseThrow();
        assertThat(finalListing.getStatus()).isEqualTo(ListingStatus.RESERVED);

        MatchEntitlement finalEntitlement =
                matchEntitlementRepository.findById(entitlement.getId()).orElseThrow();
        assertThat(finalEntitlement.getStatus()).isEqualTo(EntitlementStatus.RESERVED);
    }
}
