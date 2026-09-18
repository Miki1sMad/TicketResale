package com.miki1smad.ticketresale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class TicketResaleApplicationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void contextLoadsAndFlywayMigrates() {
        Integer count = jdbcClient
                .sql("SELECT COUNT(*) FROM schema_initialization_check")
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);
    }
}
