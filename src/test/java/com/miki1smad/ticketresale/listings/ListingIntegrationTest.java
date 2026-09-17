package com.miki1smad.ticketresale.listings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.miki1smad.ticketresale.seasontickets.EntitlementStatus;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ListingIntegrationTest extends com.miki1smad.ticketresale.BaseIntegrationTest {

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

    @Test
    void shouldCreateListingAndReserveAndExpireLifecycle() throws Exception {
        // 1. Seller registers with barcode ST-2025-003
        String sellerToken = registerAndGetToken("seller1@example.com", "ST-2025-003");

        SeasonTicket ticket =
                seasonTicketRepository.findByBarcode("ST-2025-003").orElseThrow();
        List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
        assertThat(entitlements).isNotEmpty();
        MatchEntitlement entitlement = entitlements.getFirst();
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.OWNER_HELD);

        // 2. Seller creates listing
        String listingPayload = String.format("""
                {
                    "matchEntitlementId": %d,
                    "price": 3500.00
                }
                """, entitlement.getId());

        MvcResult listingResult = mockMvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(listingPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.price").value(3500.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        Long listingId = ((Number) JsonPath.read(listingResult.getResponse().getContentAsString(), "$.id")).longValue();

        // Entitlement status in DB must now be LISTED
        MatchEntitlement reloadedEntitlement =
                matchEntitlementRepository.findById(entitlement.getId()).orElseThrow();
        assertThat(reloadedEntitlement.getStatus()).isEqualTo(EntitlementStatus.LISTED);

        // 3. Query active listings by match ID
        mockMvc.perform(get("/api/v1/listings?matchId=" + entitlement.getMatch().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(listingId))
                .andExpect(jsonPath("$[0].price").value(3500.00));

        // 4. Seller cannot reserve own listing
        String reservePayload = String.format("""
                {
                    "listingId": %d
                }
                """, listingId);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot reserve your own listing"));

        // 5. Buyer registers and reserves the listing
        String buyerToken = registerAndGetToken("buyer1@example.com", null);

        MvcResult reservationResult = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reservedPrice").value(3500.00))
                .andReturn();

        Long reservationId =
                ((Number) JsonPath.read(reservationResult.getResponse().getContentAsString(), "$.id")).longValue();

        // Verify listing and entitlement are now RESERVED
        Listing reloadedListing = listingRepository.findById(listingId).orElseThrow();
        assertThat(reloadedListing.getStatus()).isEqualTo(ListingStatus.RESERVED);
        reloadedEntitlement =
                matchEntitlementRepository.findById(entitlement.getId()).orElseThrow();
        assertThat(reloadedEntitlement.getStatus()).isEqualTo(EntitlementStatus.RESERVED);

        // 6. Another buyer tries to reserve already reserved listing -> 409 Conflict
        String buyer2Token = registerAndGetToken("buyer2@example.com", null);
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + buyer2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservePayload))
                .andExpect(status().isConflict());

        // 7. Verify reservation query endpoint
        mockMvc.perform(get("/api/v1/reservations/" + reservationId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 8. Test TTL Expiration via expirePendingReservations (simulating 15 minutes later)
        int expiredCount =
                reservationService.expirePendingReservations(Instant.now().plus(Duration.ofMinutes(15)));
        assertThat(expiredCount).isGreaterThanOrEqualTo(1);

        Reservation expiredRes = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(expiredRes.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        reloadedListing = listingRepository.findById(listingId).orElseThrow();
        assertThat(reloadedListing.getStatus()).isEqualTo(ListingStatus.ACTIVE);

        reloadedEntitlement =
                matchEntitlementRepository.findById(entitlement.getId()).orElseThrow();
        assertThat(reloadedEntitlement.getStatus()).isEqualTo(EntitlementStatus.LISTED);

        // 9. Seller cancels the restored listing
        mockMvc.perform(delete("/api/v1/listings/" + listingId).header("Authorization", "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        reloadedEntitlement =
                matchEntitlementRepository.findById(entitlement.getId()).orElseThrow();
        assertThat(reloadedEntitlement.getStatus()).isEqualTo(EntitlementStatus.OWNER_HELD);
    }
}
