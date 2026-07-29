package com.neobank.module.controller;

import com.neobank.module.integrations.idprovider.Agency;
import com.neobank.module.integrations.idprovider.CircuitBreaker;
import com.neobank.module.service.ProviderGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/provider/health} — is the outside world answering us?
 *
 * <p>An operator staring at a queue of parked cases has one question: <i>is this a run of dodgy
 * applicants, or is the provider down?</i> The cases themselves cannot tell them apart, because a
 * provider outage and a genuinely doubtful document both come out as REVIEW. This endpoint is
 * where that question gets an answer.</p>
 *
 * <p><b>Deliberately NOT a Spring {@code HealthIndicator}, and deliberately not part of
 * {@code /health}.</b> {@code /health} is what the load balancer polls to decide whether to keep
 * this container in service. If it went red when the provider went down, a provider outage would
 * take the module out of the load balancer, roll back a perfectly good deployment, and remove the
 * one screen that could have explained what was happening. A dependency being down is news; it is
 * not this service being unfit to serve.</p>
 */
@RestController
public class ProviderHealthController {

    private final ProviderGateway gateway;

    public ProviderHealthController(ProviderGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/api/v1/provider/health")
    public Map<String, Object> providerHealth() {
        Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
        gateway.breakers().forEach((agency, breaker) -> breakers.put(agency.name(), breaker));

        Map<String, Object> sources = new LinkedHashMap<>();
        for (Agency agency : Agency.values()) {
            CircuitBreaker breaker = gateway.breakers().get(agency);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", agency == Agency.NATIONAL ? "PRIMARY" : "FALLBACK");
            entry.put("circuit", breaker.state().name());
            entry.put("consecutiveFailures", breaker.consecutiveFailures());
            entry.put("lastTransitionAt", breaker.lastTransitionAt().toString());
            // Null unless the circuit is open — the one number an operator actually wants when
            // it is: "when will it try again?"
            entry.put("retryAt", breaker.retryAt() == null ? null : breaker.retryAt().toString());
            sources.put(agency.name(), entry);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sources", sources);
        body.put("callable", sources.values().stream()
                .anyMatch(source -> !"OPEN".equals(((Map<?, ?>) source).get("circuit"))));
        return body;
    }
}
