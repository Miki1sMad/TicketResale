package com.miki1smad.ticketresale.seasontickets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.miki1smad.ticketresale.users.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
class SeasonTicketIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonTicketRepository seasonTicketRepository;

    @Autowired
    private MatchEntitlementRepository matchEntitlementRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRegisterUserWithValidSeasonTicketBarcodeAndCreateEntitlements() throws Exception {
        String payload = """
                {
                    "email": "sezonac1@example.com",
                    "password": "Password123!",
                    "firstName": "Marko",
                    "lastName": "Markovic",
                    "seasonTicketBarcode": "ST-2025-001"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        SeasonTicket ticket =
                seasonTicketRepository.findByBarcode("ST-2025-001").orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(SeasonTicketStatus.ACTIVE);
        assertThat(ticket.getOwner()).isNotNull();
        assertThat(ticket.getOwner().getEmail()).isEqualTo("sezonac1@example.com");

        List<MatchEntitlement> entitlements = matchEntitlementRepository.findBySeasonTicketId(ticket.getId());
        assertThat(entitlements).isNotEmpty();
        assertThat(entitlements).allMatch(e -> e.getStatus() == EntitlementStatus.OWNER_HELD);
    }

    @Test
    void shouldFailRegistrationWhenSeasonTicketBarcodeIsInvalid() throws Exception {
        String payload = """
                {
                    "email": "invalid.barcode@example.com",
                    "password": "Password123!",
                    "firstName": "Petar",
                    "lastName": "Petrovic",
                    "seasonTicketBarcode": "NON-EXISTENT-BARCODE-999"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Nevažeći bar-kod sezonske karte. Molimo proverite unos i pokušajte ponovo."));

        // User should not exist in database due to transaction rollback
        assertThat(userRepository.findByEmail("invalid.barcode@example.com")).isEmpty();
    }

    @Test
    void shouldClaimSeasonTicketPostRegistrationAndPreventDuplicateClaim() throws Exception {
        // 1. Register a regular user without barcode
        String registerPayload = """
                {
                    "email": "sezonac2@example.com",
                    "password": "Password123!",
                    "firstName": "Nikola",
                    "lastName": "Nikolic"
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andReturn();

        String token = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        // 2. Claim an available barcode
        String claimPayload = """
                {
                    "barcode": "ST-2025-002"
                }
                """;

        mockMvc.perform(post("/api/v1/season-tickets/claim")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("ST-2025-002"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.entitlements").isArray())
                .andExpect(jsonPath("$.entitlements.length()").value(2));

        // 3. Register second user and try to claim the SAME barcode -> must fail with 409 Conflict
        String registerSecond = """
                {
                    "email": "attacker@example.com",
                    "password": "Password123!",
                    "firstName": "Luka",
                    "lastName": "Lukic"
                }
                """;

        MvcResult attackerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerSecond))
                .andExpect(status().isCreated())
                .andReturn();

        String attackerToken = JsonPath.read(attackerResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/season-tickets/claim")
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sezonska karta sa ovim bar-kodom je već preuzeta."));

        // 4. Verify my season tickets endpoint for first user
        mockMvc.perform(get("/api/v1/season-tickets/my").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].barcode").value("ST-2025-002"))
                .andExpect(jsonPath("$[0].entitlements.length()").value(2));
    }

    @Test
    void shouldDenyAvailableBarcodesWithoutAdminAndAllowForAdmin() throws Exception {
        // 1. Unauthenticated request must be denied
        mockMvc.perform(get("/api/v1/season-tickets/available-barcodes")).andExpect(status().isForbidden());

        // 2. Admin login
        String adminLogin = """
                {
                    "email": "admin@ticketresale.com",
                    "password": "Admin123!Safe"
                }
                """;
        MvcResult adminResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLogin))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = JsonPath.read(adminResult.getResponse().getContentAsString(), "$.accessToken");

        // 3. Admin can retrieve barcodes
        mockMvc.perform(get("/api/v1/season-tickets/available-barcodes")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
