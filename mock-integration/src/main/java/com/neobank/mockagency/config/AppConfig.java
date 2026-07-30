package com.neobank.mockagency.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One bean. The {@link Clock} is injected rather than called statically so a test can pin
 * {@code checkedAt} instead of asserting "some instant near now" — the same reason the module next
 * door injects one.
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
