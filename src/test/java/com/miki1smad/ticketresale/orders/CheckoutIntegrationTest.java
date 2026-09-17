package com.miki1smad.ticketresale.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class CheckoutIntegrationTest extends com.miki1smad.ticketresale.BaseIntegrationTest {

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
    private ReservationService reservationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private ResaleTicketRepository resaleTicketRepository;

    @Autowired
    private TicketTokenService ticketTokenService;

    private String registerAndGetToken(String email, String barcode) throws Exception {
        String payload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!",
                    "firstName": "Petar",
                    "lastName": "Petrovic",
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

    private Long createListingAndGetReservationId(String sellerBarcode, String sellerEmail, String buyerEmail)
            throws Exception {
        String sellerToken = registerAndGetToken(sellerEmail, sellerBarcode);

        SeasonTicket ticket =
                seasonTicketRepository.findByBarcode(sellerBarcode).orElseThrow();
        List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
        assertThat(entitlements).isNotEmpty();
        MatchEntitlement entitlement = entitlements.getFirst();

        String listingPayload = String.format("""
                {
                    "matchEntitlementId": %d,
                    "price": 3000.00
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

        return ((Number) JsonPath.read(reservationResult.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void shouldSuccessfullyCheckoutReservationAndIssueTicket() throws Exception {
        String buyerEmail = "checkout_buyer1@example.com";
        Long reservationId = createListingAndGetReservationId("ST-2025-011", "seller_chk1@example.com", buyerEmail);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """, buyerEmail);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        String buyerToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

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
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalAmount").value(3000.00))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.ticketToken").isNotEmpty())
                .andExpect(jsonPath("$.tickets").isArray())
                .andExpect(jsonPath("$.tickets[0].status").value("VALID"))
                .andReturn();

        String rawTicketToken = JsonPath.read(checkoutResult.getResponse().getContentAsString(), "$.ticketToken");
        assertThat(rawTicketToken).startsWith("TKT_");
        assertThat(ticketTokenService.validateTokenHmac(rawTicketToken)).isTrue();

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);

        Listing listing =
                listingRepository.findById(reservation.getListing().getId()).orElseThrow();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);

        MatchEntitlement entitlement = matchEntitlementRepository
                .findById(listing.getMatchEntitlement().getId())
                .orElseThrow();
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.RESOLD);

        String expectedHash = ticketTokenService.hashToken(rawTicketToken);
        ResaleTicket ticket =
                resaleTicketRepository.findByBarcodeHash(expectedHash).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VALID);
        assertThat(ticket.getBuyer().getId()).isNotNull();

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByReservationId(reservationId);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(transactions.getFirst().getCardLast4()).isEqualTo("4444");

        // Verify my-tickets endpoint
        mockMvc.perform(get("/api/v1/orders/my-tickets").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(ticket.getId()))
                .andExpect(jsonPath("$[0].status").value("VALID"));

        // Verify my-orders endpoint
        mockMvc.perform(get("/api/v1/orders/my").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].idempotencyKey").value(idempotencyKey));
    }

    @Test
    void shouldFailCheckoutWhenCardEndsIn0000AndKeepReservationPending() throws Exception {
        String buyerEmail = "checkout_buyer2@example.com";
        Long reservationId = createListingAndGetReservationId("ST-2025-012", "seller_chk2@example.com", buyerEmail);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """, buyerEmail);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        String buyerToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        String idempotencyKey = UUID.randomUUID().toString();
        String failedCheckoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233330000"
                }
                """, reservationId);

        // Attempt checkout with 0000 card -> expect 400 Bad Request
        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedCheckoutPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("declined")));

        // Reservation must remain PENDING
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);

        // Listing must remain RESERVED
        Listing listing =
                listingRepository.findById(reservation.getListing().getId()).orElseThrow();
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.RESERVED);

        // Failed PaymentTransaction must be recorded
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByReservationId(reservationId);
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(transactions.getFirst().getCardLast4()).isEqualTo("0000");

        // Now retry with valid card -> succeeds
        String successCheckoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233331111"
                }
                """, reservationId);

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(successCheckoutPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }

    @Test
    void shouldBeIdempotentWhenSameIdempotencyKeyIsUsed() throws Exception {
        String buyerEmail = "checkout_buyer3@example.com";
        Long reservationId = createListingAndGetReservationId("ST-2025-013", "seller_chk3@example.com", buyerEmail);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """, buyerEmail);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        String buyerToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        String idempotencyKey = "fixed-idempotency-key-" + UUID.randomUUID();
        String checkoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233334444"
                }
                """, reservationId);

        // First attempt -> 201 Created
        MvcResult firstResult = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Long firstOrderId =
                ((Number) JsonPath.read(firstResult.getResponse().getContentAsString(), "$.id")).longValue();

        // Second attempt with exact same key -> Returns existing order
        MvcResult secondResult = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Long secondOrderId =
                ((Number) JsonPath.read(secondResult.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(firstOrderId).isEqualTo(secondOrderId);

        // Verify only 1 order and 1 ticket created for this reservation
        assertThat(orderRepository.findByReservationId(reservationId)).isPresent();
        List<ResaleTicket> tickets = resaleTicketRepository.findByOrderId(firstOrderId);
        assertThat(tickets).hasSize(1);
    }

    @Test
    void shouldRejectCheckoutForExpiredReservation() throws Exception {
        String buyerEmail = "checkout_buyer4@example.com";
        Long reservationId = createListingAndGetReservationId("ST-2025-014", "seller_chk4@example.com", buyerEmail);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """, buyerEmail);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        String buyerToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        // Expire reservation via worker simulation
        reservationService.expirePendingReservations(Instant.now().plus(Duration.ofMinutes(15)));

        String checkoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233334444"
                }
                """, reservationId);

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutPayload))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectCheckoutFromDifferentBuyer() throws Exception {
        String buyer1Email = "checkout_buyer5@example.com";
        Long reservationId = createListingAndGetReservationId("ST-2025-015", "seller_chk5@example.com", buyer1Email);

        // Buyer 2 tries to checkout Buyer 1's reservation
        String buyer2Token = registerAndGetToken("checkout_buyer6@example.com", null);

        String checkoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233334444"
                }
                """, reservationId);

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyer2Token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You do not own this reservation"));
    }

    @Test
    void shouldRejectCheckoutWithoutIdempotencyKey() throws Exception {
        String buyerEmail = "checkout_buyer7@example.com";
        Long reservationId = createListingAndGetReservationId("ST-2025-016", "seller_chk6@example.com", buyerEmail);

        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """, buyerEmail);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();
        String buyerToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        String checkoutPayload = String.format("""
                {
                    "reservationId": %d,
                    "paymentMethod": "CARD",
                    "cardNumber": "4111222233334444"
                }
                """, reservationId);

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
    }
}
