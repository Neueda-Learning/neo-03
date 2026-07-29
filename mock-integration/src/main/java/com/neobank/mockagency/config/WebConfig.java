package com.neobank.mockagency.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the control page — and anything else on a localhost port — call the admin API.
 *
 * <p>Patterns rather than a fixed list, for the reason the module's own {@code WebConfig} spells
 * out: a hard-coded port breaks as soon as the stack moves, and it breaks only on writes, so every
 * curl check still passes while the page silently stops working.</p>
 *
 * <p>{@code PUT} is in the method list here where the module's is {@code GET/POST/OPTIONS} — the
 * dials are a PUT, and leaving it out is exactly the bug that would make the control page's buttons
 * do nothing.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                .allowedHeaders("*");
    }
}
