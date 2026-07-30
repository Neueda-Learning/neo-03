package com.neobank.mockagency;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.mockagency.service.AgencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The context loads with no database on the classpath.
 *
 * <p>Worth its own test: this service deliberately drops JPA, Liquibase and the MySQL driver, and
 * the failure mode of getting that wrong is Spring Boot trying to auto-configure a DataSource and
 * dying at startup with a message about an embedded database — which reads as a missing dependency
 * rather than an unwanted one.</p>
 */
@SpringBootTest
class MockAgencyApplicationTests {

    @Autowired
    private AgencyService agencies;

    @Test
    @DisplayName("The context starts, with both agencies healthy and idle")
    void contextLoads() {
        assertThat(agencies.allConfigs()).hasSize(2);
        assertThat(agencies.callCounts().values()).allMatch(count -> count == 0);
    }
}
