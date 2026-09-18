package com.miki1smad.ticketresale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlement;
import com.miki1smad.ticketresale.seasontickets.MatchEntitlementRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicket;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketRepository;
import com.miki1smad.ticketresale.seasontickets.SeasonTicketStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class ModulithEventsAndEmailIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonTicketRepository seasonTicketRepository;

    @Autowired
    private MatchEntitlementRepository matchEntitlementRepository;

    private String registerAndGetToken(String email, String barcode) throws Exception {
        String payload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!",
                    "firstName": "Milan",
                    "lastName": "Jovanovic",
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
    void shouldSendEmailsForRegistrationAndLoginEvents() throws Exception {
        String userEmail = "modulith_user_" + UUID.randomUUID() + "@example.com";

        // 1. Register user
        registerAndGetToken(userEmail, null);

        // Verify registration email received asynchronously
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(mailSender, atLeastOnce()).send(captor.capture());
            List<SimpleMailMessage> sentMessages = captor.getAllValues();
            assertThat(sentMessages)
                    .anyMatch(msg -> msg.getTo() != null
                            && List.of(msg.getTo()).contains(userEmail)
                            && msg.getSubject() != null
                            && msg.getSubject().contains("Dobrodošli na TicketResale"));
        });

        // 2. Login user
        String loginPayload = String.format("""
                {
                    "email": "%s",
                    "password": "Password123!"
                }
                """, userEmail);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk());

        // Verify login alert email received asynchronously
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(mailSender, atLeastOnce()).send(captor.capture());
            List<SimpleMailMessage> sentMessages = captor.getAllValues();
            assertThat(sentMessages)
                    .anyMatch(msg -> msg.getTo() != null
                            && List.of(msg.getTo()).contains(userEmail)
                            && msg.getSubject() != null
                            && msg.getSubject().contains("Nova prijava na vaš TicketResale nalog"));
        });
    }

    @Test
    void shouldSendEmailsForListingAndCompletePurchaseFlow() throws Exception {
        String sellerEmail = "modulith_seller_" + UUID.randomUUID() + "@example.com";
        String buyerEmail = "modulith_buyer_" + UUID.randomUUID() + "@example.com";

        SeasonTicket ticket = seasonTicketRepository.findAll().stream()
                .filter(t -> t.getStatus() == SeasonTicketStatus.UNCLAIMED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No unclaimed season ticket available"));
        String seasonTicketBarcode = ticket.getBarcode();

        // 1. Seller registers with season ticket
        String sellerToken = registerAndGetToken(sellerEmail, seasonTicketBarcode);

        List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
        MatchEntitlement entitlement = entitlements.getFirst();

        // 2. Seller lists ticket
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

        // Verify seller received listing confirmation email
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(mailSender, atLeastOnce()).send(captor.capture());
            List<SimpleMailMessage> sentMessages = captor.getAllValues();
            assertThat(sentMessages)
                    .anyMatch(msg -> msg.getTo() != null
                            && List.of(msg.getTo()).contains(sellerEmail)
                            && msg.getSubject() != null
                            && msg.getSubject().contains("Vaša karta je oglašena"));
        });

        // 3. Buyer registers and reserves ticket
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

        // 4. Buyer checks out
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
                .andExpect(status().isCreated());

        // Verify:
        // A) Seller receives sale confirmation email
        // B) Buyer receives order confirmation email
        // C) Buyer receives separate barcode ticket email
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(mailSender, atLeastOnce()).send(captor.capture());
            List<SimpleMailMessage> allSent = captor.getAllValues();

            // A) Seller sold email
            assertThat(allSent)
                    .anyMatch(msg -> msg.getTo() != null
                            && List.of(msg.getTo()).contains(sellerEmail)
                            && msg.getSubject() != null
                            && msg.getSubject().contains("Vaša karta je uspešno prodata"));

            // B) Buyer order confirmation email
            assertThat(allSent)
                    .anyMatch(msg -> msg.getTo() != null
                            && List.of(msg.getTo()).contains(buyerEmail)
                            && msg.getSubject() != null
                            && msg.getSubject().contains("Potvrda kupovine"));

            // C) Buyer barcode email
            assertThat(allSent)
                    .anyMatch(msg -> msg.getTo() != null
                            && List.of(msg.getTo()).contains(buyerEmail)
                            && msg.getSubject() != null
                            && msg.getSubject().contains("Vaša ulaznica i bar-kod")
                            && msg.getText() != null
                            && msg.getText().contains("TKT_"));
        });
    }
}
