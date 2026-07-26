package com.neobank.module.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets this service's React UI (its nginx container, or the Vite dev server) call the API.
 *
 * <p><b>Patterns, not a fixed list.</b> The browser sends an {@code Origin} header on every
 * POST, and Spring answers {@code 403 Invalid CORS request} if that origin is not allowed —
 * so a hard-coded port breaks the UI as soon as the stack runs on a different one. Same-origin
 * GETs send no {@code Origin}, so it fails only on writes and passes every curl check.
 *
 * <p>This is a single-user local stack with no auth, so any localhost port is fine.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
