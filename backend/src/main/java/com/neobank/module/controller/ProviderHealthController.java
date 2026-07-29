package com.neobank.module.controller;

import com.neobank.module.integrations.idprovider.Agency;
import com.neobank.module.integrations.idprovider.CircuitBreaker;
import com.neobank.module.integrations.idprovider.ProviderAdminClient;
import com.neobank.module.integrations.idprovider.ProviderAdminClient.ProviderPreset;
import com.neobank.module.service.ProviderGateway;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final ProviderAdminClient providerAdmin;

    public ProviderHealthController(ProviderGateway gateway, ProviderAdminClient providerAdmin) {
        this.gateway = gateway;
        this.providerAdmin = providerAdmin;
    }

    @GetMapping("/api/v1/provider/health")
    public Map<String, Object> providerHealth() {
        Map<Agency, CircuitBreaker> breakers = gateway.breakers();
        Map<String, Object> sources = new LinkedHashMap<>();
        for (Agency agency : Agency.values()) {
            CircuitBreaker breaker = breakers.get(agency);
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

    /**
     * What the mocked agencies are currently set to.
     *
     * <p>Proxied rather than fetched by the browser: on AWS the mock is a container inside this
     * task with no route of its own, so this backend is the only thing that can reach it.</p>
     */
    @GetMapping("/api/v1/provider/config")
    public Map<String, Object> providerConfig() {
        return providerAdmin.readConfig();
    }

    /**
     * Point the mocked agencies at a preset.
     *
     * <p>NOT a back door: it configures a mocked dependency, it cannot set a decision, and every
     * application still travels the whole path — dispatch, retry ladder, callback — whatever it is
     * set to. The outcomes it produces are produced by the real rules reading a real response.</p>
     *
     * @param primaryOnly leaves the fallback healthy, which is the failover demo. Without it a
     *                    {@code DOWN} preset takes both agencies out, which is the outage.
     */
    @PutMapping("/api/v1/provider/config")
    public Map<String, Object> setProviderConfig(
            @RequestParam ProviderPreset preset,
            @RequestParam(defaultValue = "false") boolean primaryOnly) {
        providerAdmin.apply(preset, primaryOnly);
        return providerAdmin.readConfig();
    }
}
