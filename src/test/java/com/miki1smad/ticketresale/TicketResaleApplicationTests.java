package com.miki1smad.ticketresale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketResaleApplicationTests {

    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        postgres.start();
    }

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void contextLoadsAndFlywayMigrates() {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM schema_initialization_check")
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);
    }

}
