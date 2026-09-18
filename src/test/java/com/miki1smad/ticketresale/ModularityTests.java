package com.miki1smad.ticketresale;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(TicketResaleApplication.class);

    @Test
    void verifyModularity() {
        modules.verify();
    }

    @Test
    void createModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
