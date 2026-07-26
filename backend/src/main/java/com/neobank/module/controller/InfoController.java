package com.neobank.module.controller;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /info} — who this module is, and what it is faking.
 *
 * <p>{@code mockedDependencies} is the machine-readable half of the "what has been mocked"
 * register every team must deliver: list the external systems you stand in for
 * ({@code MOCKED_DEPENDENCIES=id-verification-provider,sanctions-list}) and the answer is
 * always current, because it is configuration rather than a paragraph in a document that
 * goes stale. An empty list is a claim that nothing is mocked — make sure that is true.</p>
 */
@RestController
public class InfoController {

    @Value("${service.id:neo03}")
    private String serviceId;

    @Value("${service.name:Identity Verification (KYC)}")
    private String serviceName;

    @Value("${service.team:unassigned}")
    private String team;

    @Value("${service.domain:unassigned}")
    private String domain;

    @Value("${service.version:0.1.0-SNAPSHOT}")
    private String version;

    @Value("${service.orchestrator-url:http://localhost:9000}")
    private String orchestratorUrl;

    @Value("${service.mocked-dependencies:}")
    private String mockedDependencies;

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceId", serviceId);
        body.put("service", serviceName);
        body.put("team", team);
        body.put("domain", domain);
        body.put("version", version);
        body.put("orchestratorUrl", orchestratorUrl);
        body.put("mockedDependencies", split(mockedDependencies));
        return body;
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
