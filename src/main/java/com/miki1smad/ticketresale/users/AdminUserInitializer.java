package com.miki1smad.ticketresale.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements CommandLineRunner {

    private final UserService userService;

    @Value("${ticketresale.admin.email:admin@ticketresale.com}")
    private String adminEmail;

    @Value("${ticketresale.admin.password:Admin123!Safe}")
    private String adminPassword;

    @Value("${ticketresale.admin.first-name:System}")
    private String adminFirstName;

    @Value("${ticketresale.admin.last-name:Admin}")
    private String adminLastName;

    @Override
    public void run(String... args) {
        if (userService.findByEmail(adminEmail).isEmpty()) {
            userService.registerUser(adminEmail, adminPassword, adminFirstName, adminLastName, Role.ADMIN);
            log.info("Initialized default admin user: {}", adminEmail);
        }
    }
}
