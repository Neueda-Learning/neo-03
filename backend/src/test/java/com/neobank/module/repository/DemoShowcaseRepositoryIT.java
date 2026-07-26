package com.neobank.module.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.neobank.module.model.Decision;
import com.neobank.module.model.DemoShowcase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * INTEGRATION TEST (name ends in {@code IT} → runs on {@code ./mvnw verify}, needs Docker).
 *
 * <p>Testcontainers boots a real MySQL 8.4, this module's Liquibase change set creates
 * {@code demo_showcase} on it, and Hibernate runs {@code ddl-auto=validate} against that real DDL.
 * It catches what H2 hides — {@code TIMESTAMP}↔{@code Instant} and column widths — which is exactly
 * the class of bug that otherwise only appears on {@code docker compose up}.</p>
 *
 * <p><b>Keep an IT like this when you replace the table.</b> It is the only test that proves your
 * change set and your entity agree on a database that behaves like the deployed one.</p>
 *
 * <p>{@code disabledWithoutDocker = true}: with Docker stopped this is SKIPPED, not failed.</p>
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional // roll back each test so methods don't leak rows into one another
class DemoShowcaseRepositoryIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("neo_03");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    DemoShowcaseRepository demoShowcase;

    @Test
    void schemaValidatesAndStartsEmpty() {
        // Reaching here proves Liquibase applied 001 and ddl-auto=validate passed on real MySQL.
        assertThat(demoShowcase.findAll()).isEmpty();
    }

    @Test
    void aRowRoundTripsThroughRealMysql() {
        DemoShowcase saved = demoShowcase.saveAndFlush(
                new DemoShowcase("APP-1", Decision.ACCEPTED));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();   // @PrePersist ran

        DemoShowcase reloaded = demoShowcase.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getApplicationId()).isEqualTo("APP-1");
        assertThat(reloaded.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void theBoardOrdersNewestFirst() {
        demoShowcase.saveAndFlush(new DemoShowcase("APP-OLD", Decision.ACCEPTED));
        demoShowcase.saveAndFlush(new DemoShowcase("APP-NEW", Decision.REJECTED));

        assertThat(demoShowcase.findAllByOrderByCreatedAtDescIdDesc())
                .extracting(DemoShowcase::getApplicationId)
                .containsExactly("APP-NEW", "APP-OLD");
    }
}
