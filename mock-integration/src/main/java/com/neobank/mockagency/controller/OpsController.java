package com.neobank.mockagency.controller;

import com.neobank.mockagency.model.Agency;
import com.neobank.mockagency.service.AgencyService;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /health} and {@code GET /info} — the same two endpoints every service in this system
 * serves, so the compose healthcheck and anyone poking at the stack find what they expect.
 *
 * <p><b>{@code /health} reports on this process only.</b> It is deliberately not affected by the
 * dials: a kill switch is this service doing its job, not this service being broken. If
 * {@code /health} went red when the kill switch went on, Docker would restart the container
 * mid-demo and turn the dial back off for you.</p>
 */
@RestController
public class OpsController {

    private final AgencyService agencies;
    private final Clock clock;

    @Value("${service.name:Identity sources (mock)}")
    private String serviceName;

    @Value("${service.version:0.1.0-SNAPSHOT}")
    private String version;

    public OpsController(AgencyService agencies, Clock clock) {
        this.agencies = agencies;
        this.clock = clock;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", serviceName);
        body.put("timestamp", clock.instant().toString());
        return body;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", serviceName);
        body.put("version", version);
        body.put("agencies", Arrays.stream(Agency.values()).map(this::describe).toList());
        body.put("calls", agencies.callCounts());
        // Deliberately stated rather than implied: this service exists to be replaced. Anyone
        // reading /info should know it is not a real register and what a real one would be.
        body.put("standsInFor", List.of(
                "national identity register (passport / national ID verification)",
                "tax authority identity confirmation"));
        return body;
    }

    private Map<String, Object> describe(Agency agency) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("agency", agency.name());
        entry.put("slug", agency.slug());
        entry.put("path", "/api/v1/agencies/" + agency.slug() + "/verifications");
        entry.put("config", agencies.configFor(agency));
        return entry;
    }
}
