package com.miki1smad.ticketresale.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends com.miki1smad.ticketresale.BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRegisterUserAndReturnTokens() throws Exception {
        String payload = """
                {
                    "email": "john.doe@example.com",
                    "password": "Password123!",
                    "firstName": "John",
                    "lastName": "Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    void shouldLoginRegisteredUser() throws Exception {
        String registerPayload = """
                {
                    "email": "jane.doe@example.com",
                    "password": "Password123!",
                    "firstName": "Jane",
                    "lastName": "Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String loginPayload = """
                {
                    "email": "jane.doe@example.com",
                    "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void shouldLoginDefaultAdminUser() throws Exception {
        String adminLoginPayload = """
                {
                    "email": "admin@ticketresale.com",
                    "password": "Admin123!Safe"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLoginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        String adminLoginPayload = """
                {
                    "email": "admin@ticketresale.com",
                    "password": "Admin123!Safe"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLoginPayload))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        String refreshToken = JsonPath.read(responseContent, "$.refreshToken");
        assertThat(refreshToken).isNotBlank();

        String refreshPayload = String.format("""
                {
                    "refreshToken": "%s"
                }
                """, refreshToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldRejectRefreshTokenWhenUsedAsBearerToken() throws Exception {
        String adminLoginPayload = """
                {
                    "email": "admin@ticketresale.com",
                    "password": "Admin123!Safe"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLoginPayload))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = loginResult.getResponse().getContentAsString();
        String refreshToken = JsonPath.read(responseContent, "$.refreshToken");

        // Attempting to access protected endpoint (/api/v1/orders/my) with Refresh Token as Bearer token
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/orders/my")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isForbidden());
    }
}
