package com.neobank.module.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Infrastructure beans. Just the HTTP client this module calls the orchestrator back with.
 *
 * <p>The thread pool the decision runs on is Spring Boot's own
 * {@code applicationTaskExecutor} — no bean needed here. Size and naming are properties:
 * {@code spring.task.execution.*} in {@code application.yml}.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}
