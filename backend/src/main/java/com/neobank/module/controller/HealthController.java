package com.neobank.module.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /health} — a small, stable shape that the compose healthcheck, the
 * orchestrator's service board and this service's own page all read directly. It probes
 * the database, so a green light means "I can actually serve requests".
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final String serviceId;
    private final String serviceName;

    public HealthController(DataSource dataSource, Environment env) {
        this.dataSource = dataSource;
        this.serviceId = env.getProperty("service.id", "neo03");
        this.serviceName = env.getProperty("service.name", "Identity Verification (KYC)");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = probeDatabase();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("serviceId", serviceId);
        body.put("service", serviceName);
        body.put("timestamp", Instant.now().toString());
        body.put("database", Map.of("status", dbUp ? "UP" : "DOWN"));

        return ResponseEntity
                .status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    private boolean probeDatabase() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
