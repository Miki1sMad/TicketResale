package com.miki1smad.ticketresale.events;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class EventIntegrationTest {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private MockMvc mockMvc;

    private String getAdminToken() throws Exception {
        String adminLogin = """
                {
                    "email": "admin@ticketresale.com",
                    "password": "Admin123!Safe"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLogin))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void shouldDenyClubCreationWithoutAdminRole() throws Exception {
        String payload = """
                {
                    "name": "FK Partizan",
                    "city": "Belgrade"
                }
                """;

        mockMvc.perform(post("/api/v1/clubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateClubsStadiumAndMatchAsAdminAndAllowPublicRead() throws Exception {
        String token = getAdminToken();

        // 1. Create Home Club (Admin)
        String homeClubPayload = """
                {
                    "name": "FK TSC",
                    "city": "Backa Topola"
                }
                """;

        MvcResult homeResult = mockMvc.perform(post("/api/v1/clubs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(homeClubPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("FK TSC"))
                .andReturn();

        Number homeClubId = JsonPath.read(homeResult.getResponse().getContentAsString(), "$.id");

        // 2. Create Away Club (Admin)
        String awayClubPayload = """
                {
                    "name": "FK Vojvodina",
                    "city": "Novi Sad"
                }
                """;

        MvcResult awayResult = mockMvc.perform(post("/api/v1/clubs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(awayClubPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number awayClubId = JsonPath.read(awayResult.getResponse().getContentAsString(), "$.id");

        // 3. Public user can view clubs
        mockMvc.perform(get("/api/v1/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Create Stadium (Admin)
        String stadiumPayload = String.format("""
                {
                    "clubId": %d,
                    "name": "TSC Arena",
                    "city": "Backa Topola",
                    "capacity": 4500
                }
                """, homeClubId.longValue());

        MvcResult stadiumResult = mockMvc.perform(post("/api/v1/stadiums")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stadiumPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number stadiumId = JsonPath.read(stadiumResult.getResponse().getContentAsString(), "$.id");

        // 5. Public user can view stadiums
        mockMvc.perform(get("/api/v1/stadiums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 6. Create Match (Admin)
        Instant kickoff = Instant.now().plus(7, ChronoUnit.DAYS);
        String matchPayload =
                String.format("""
                {
                    "homeClubId": %d,
                    "awayClubId": %d,
                    "stadiumId": %d,
                    "kickoffTime": "%s",
                    "season": "2025/2026"
                }
                """, homeClubId.longValue(), awayClubId.longValue(), stadiumId.longValue(), kickoff);

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.season").value("2025/2026"))
                .andExpect(jsonPath("$.homeClubName").value("FK TSC"))
                .andExpect(jsonPath("$.awayClubName").value("FK Vojvodina"));

        // 7. Public user can view upcoming matches
        mockMvc.perform(get("/api/v1/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
