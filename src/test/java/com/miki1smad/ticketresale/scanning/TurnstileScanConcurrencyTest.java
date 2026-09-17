package com.miki1smad.ticketresale.scanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.miki1smad.ticketresale.events.Match;
import com.miki1smad.ticketresale.events.MatchRepository;
import com.miki1smad.ticketresale.listings.ListingRepository;
import com.miki1smad.ticketresale.listings.ReservationRepository;
import com.miki1smad.ticketresale.orders.OrderRepository;
import com.miki1smad.ticketresale.orders.PaymentTransactionRepository;
import com.miki1smad.ticketresale.orders.ResaleTicket;
import com.miki1smad.ticketresale.orders.ResaleTicketRepository;
import com.miki1smad.ticketresale.orders.TicketStatus;
import com.miki1smad.ticketresale.orders.TicketTokenService;
import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketRepository;
import com.miki1smad.ticketresale.users.Role;
import com.miki1smad.ticketresale.users.User;
import com.miki1smad.ticketresale.users.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=50")
class TurnstileScanConcurrencyTest extends com.miki1smad.ticketresale.BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private SeasonTicketRepository seasonTicketRepository;

    @Autowired
    private MatchEntitlementRepository matchEntitlementRepository;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private ResaleTicketRepository resaleTicketRepository;

    @Autowired
    private TurnstileScanLogRepository turnstileScanLogRepository;

    @Autowired
    private TicketTokenService ticketTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String operatorToken;
    private String normalUserToken;

    @BeforeEach
    void setUp() throws Exception {
        // Create stadium operator user if not exists
        String operatorEmail = "operator_gate@ticketresale.com";
        if (userRepository.findByEmail(operatorEmail).isEmpty()) {
            userRepository.save(User.builder()
                    .email(operatorEmail)
                    .password(passwordEncoder.encode("Password123!"))
                    .firstName("Gate")
                    .lastName("Operator")
                    .role(Role.STADIUM_OPERATOR)
                    .build());
        }

        operatorToken = loginAndGetToken(operatorEmail, "Password123!");

        // Create standard user
        String userEmail = "normal_user@ticketresale.com";
        if (userRepository.findByEmail(userEmail).isEmpty()) {
            userRepository.save(User.builder()
                    .email(userEmail)
                    .password(passwordEncoder.encode("Password123!"))
                    .firstName("Normal")
                    .lastName("User")
                    .role(Role.USER)
                    .build());
        }

        normalUserToken = loginAndGetToken(userEmail, "Password123!");
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "%s"
                }
                """, email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");
    }

    private String registerAndGetToken(String email, String barcode) throws Exception {
        String payload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!",
                    "firstName": "Marko",
                    "lastName": "Markovic",
                    "seasonTicketBarcode": "%s"
                }
                """, email, barcode != null ? barcode : "");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createResaleTicketAndGetRawBarcode(String seasonBarcode, String sellerEmail, String buyerEmail)
            throws Exception {
        String sellerToken = registerAndGetToken(sellerEmail, seasonBarcode);
        SeasonTicket ticket =
                seasonTicketRepository.findByBarcode(seasonBarcode).orElseThrow();
        List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
        MatchEntitlement entitlement = entitlements.getFirst();

        String listingPayload = String.format("""
                {
                    "matchEntitlementId": %d,
                    "price": 2500.00
                }
                """, entitlement.getId());

        MvcResult listingResult = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload))
                .andExpect(status().isCreated())
                .andReturn();
        Long listingId = ((Number) JsonPath.read(listingResult.getResponse().getContentAsString(), "$.id")).longValue();

        String buyerToken = registerAndGetToken(buyerEmail, null);
        String reservePayload = String.format("""
                {
                    "listingId": %d
                }
                """, listingId);

        MvcResult reservationResult = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservePayload))
                .andExpect(status().isCreated())
                .andReturn();
        Long reservationId =
                ((Number) JsonPath.read(reservationResult.getResponse().getContentAsString(), "$.id")).longValue();

        String idempotencyKey = UUID.randomUUID().toString();
        String checkoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233334444"
                }
                """, reservationId);

        MvcResult checkoutResult = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutPayload))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(checkoutResult.getResponse().getContentAsString(), "$.ticketToken");
    }

    @Test
    void shouldPreventDoubleEntryOnConcurrentScansForResaleTicket() throws Exception {
        String seasonBarcode = "ST-2025-017";
        String sellerEmail = "turnstile_seller1@ticketresale.com";
        String buyerEmail = "turnstile_buyer1@ticketresale.com";

        String rawBarcode = createResaleTicketAndGetRawBarcode(seasonBarcode, sellerEmail, buyerEmail);
        Match match = matchRepository.findAll().getFirst();

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final String turnstileId = "TURNSTILE-GATE-" + (i % 3 + 1);
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    String requestPayload = String.format("""
                            {
                                "barcode": "%s",
                                "turnstileId": "%s",
                                "matchId": %d
                            }
                            """, rawBarcode, turnstileId, match.getId());

                    MvcResult result = mockMvc.perform(post("/api/v1/turnstile/validate")
                                    .header("Authorization", "Bearer " + operatorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestPayload))
                            .andReturn();

                    statusCodes.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Exactly 1 thread must be GRANTED (200 OK)
        long grantedCount = statusCodes.stream().filter(code -> code == 200).count();
        // All other threads must be rejected with 409 Conflict (ALREADY_USED)
        long conflictCount = statusCodes.stream().filter(code -> code == 409).count();

        assertThat(grantedCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(numberOfThreads - 1);

        // Verify ticket status in DB is USED
        String hash = ticketTokenService.hashToken(rawBarcode);
        ResaleTicket ticket = resaleTicketRepository.findByBarcodeHash(hash).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(ticket.getScannedAt()).isNotNull();
        assertThat(ticket.getTurnstileId()).isNotNull();

        // Verify audit logs in turnstile_scan_logs
        List<TurnstileScanLog> logs = turnstileScanLogRepository.findByMatchId(match.getId());
        List<TurnstileScanLog> ticketLogs =
                logs.stream().filter(l -> hash.equals(l.getBarcodeHash())).toList();

        assertThat(ticketLogs).hasSize(numberOfThreads);
        long grantedLogs = ticketLogs.stream()
                .filter(l -> "GRANTED".equals(l.getScanResult()))
                .count();
        long alreadyUsedLogs = ticketLogs.stream()
                .filter(l -> "ALREADY_USED".equals(l.getScanResult()))
                .count();
        assertThat(grantedLogs).isEqualTo(1);
        assertThat(alreadyUsedLogs).isEqualTo(numberOfThreads - 1);
    }

    @Test
    void shouldPreventDoubleEntryOnConcurrentScansForSeasonTicket() throws Exception {
        String seasonBarcode = "ST-2025-018";
        String ownerEmail = "season_owner_gate@ticketresale.com";
        registerAndGetToken(ownerEmail, seasonBarcode);

        Match match = matchRepository.findAll().getFirst();

        int numberOfThreads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            final String turnstileId = "TURNSTILE-GATE-ST-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    String requestPayload = String.format("""
                            {
                                "barcode": "%s",
                                "turnstileId": "%s",
                                "matchId": %d
                            }
                            """, seasonBarcode, turnstileId, match.getId());

                    MvcResult result = mockMvc.perform(post("/api/v1/turnstile/validate")
                                    .header("Authorization", "Bearer " + operatorToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestPayload))
                            .andReturn();

                    statusCodes.add(result.getResponse().getStatus());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        long grantedCount = statusCodes.stream().filter(code -> code == 200).count();
        long conflictCount = statusCodes.stream().filter(code -> code == 409).count();

        assertThat(grantedCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(numberOfThreads - 1);

        // Verify MatchEntitlement status in DB is USED
        SeasonTicket seasonTicket =
                seasonTicketRepository.findByBarcode(seasonBarcode).orElseThrow();
        MatchEntitlement entitlement = matchEntitlementRepository
                .findBySeasonTicketIdAndMatchId(seasonTicket.getId(), match.getId())
                .orElseThrow();
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.USED);
    }

    @Test
    void shouldRejectResoldSeasonTicketAndAllowResaleTicket() throws Exception {
        String seasonBarcode = "ST-2025-019";
        String sellerEmail = "seller_resold@ticketresale.com";
        String buyerEmail = "buyer_resold@ticketresale.com";

        String resaleBarcode = createResaleTicketAndGetRawBarcode(seasonBarcode, sellerEmail, buyerEmail);
        Match match = matchRepository.findAll().getFirst();

        // 1. Scan the original season ticket card -> must be rejected with 409 Conflict
        String seasonScanPayload = String.format("""
                {
                    "barcode": "%s",
                    "turnstileId": "TURNSTILE-NORTH",
                    "matchId": %d
                }
                """, seasonBarcode, match.getId());

        mockMvc.perform(post("/api/v1/turnstile/validate")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(seasonScanPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("preprodato")));

        // 2. Scan the resale ticket -> must be GRANTED with 200 OK
        String resaleScanPayload = String.format("""
                {
                    "barcode": "%s",
                    "turnstileId": "TURNSTILE-NORTH",
                    "matchId": %d
                }
                """, resaleBarcode, match.getId());

        mockMvc.perform(post("/api/v1/turnstile/validate")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resaleScanPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("GRANTED"))
                .andExpect(jsonPath("$.ticketType").value("RESALE_TICKET"))
                .andExpect(jsonPath("$.seatInfo.sectionName").isNotEmpty());
    }

    @Test
    void shouldRejectCounterfeitOrTamperedBarcode() throws Exception {
        Match match = matchRepository.findAll().getFirst();
        String fakeBarcode = "TKT_99999999-9999-9999-9999-999999999999_fakeinvalidhmacsignature123456";

        String requestPayload = String.format("""
                {
                    "barcode": "%s",
                    "turnstileId": "TURNSTILE-EAST",
                    "matchId": %d
                }
                """, fakeBarcode, match.getId());

        mockMvc.perform(post("/api/v1/turnstile/validate")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Neispravan ili falsifikovan bar-kod"));
    }

    @Test
    void shouldRejectTicketForWrongMatch() throws Exception {
        String seasonBarcode = "ST-2025-020";
        String sellerEmail = "seller_wrong_match@ticketresale.com";
        String buyerEmail = "buyer_wrong_match@ticketresale.com";

        String rawBarcode = createResaleTicketAndGetRawBarcode(seasonBarcode, sellerEmail, buyerEmail);

        // Send non-existent match ID
        String requestPayload = String.format("""
                {
                    "barcode": "%s",
                    "turnstileId": "TURNSTILE-EAST",
                    "matchId": 999999
                }
                """, rawBarcode);

        mockMvc.perform(post("/api/v1/turnstile/validate")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Utakmica nije pronađena")));
    }

    @Test
    void shouldForbidNonOperatorUserFromValidatingAtTurnstile() throws Exception {
        Match match = matchRepository.findAll().getFirst();

        String requestPayload = String.format("""
                {
                    "barcode": "ST-2025-001",
                    "turnstileId": "TURNSTILE-EAST",
                    "matchId": %d
                }
                """, match.getId());

        mockMvc.perform(post("/api/v1/turnstile/validate")
                        .header("Authorization", "Bearer " + normalUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isForbidden());
    }
}
