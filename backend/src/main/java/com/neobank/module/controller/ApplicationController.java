package com.neobank.module.controller;

import com.neobank.module.dto.DemoShowcaseView;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.service.ApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * This module's entire HTTP surface: one endpoint the orchestrator calls, one your own UI reads.
 *
 * <p><b>Accept now, work later.</b> {@code POST} answers {@code 202} immediately and hands the
 * application to {@link ApplicationService} — which does the work off the request thread and then
 * PUTs the outcome back to the orchestrator. Never do the work inside
 * {@link #processApplication}: the orchestrator is holding a connection open, and a module that
 * blocks turns a fast journey into a slow one.</p>
 *
 * <p>Add the endpoints your operator screen needs — a search, a manual override, a detail lookup —
 * here or in a new controller. Leave the {@code POST} alone: its shape is the contract.</p>
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applications;
    private final String serviceId;

    public ApplicationController(ApplicationService applications,
                                 @Value("${service.id:neo03}") String serviceId) {
        this.applications = applications;
        this.serviceId = serviceId;
    }

    /**
     * The contract entry point. {@code 202} means "received and working on it" — the real answer
     * arrives later, as a status update on the application.
     *
     * <p>{@code @Valid} rejects an envelope with no {@code applicationId} as {@code 400} before any
     * work starts: an application this module cannot report on is not worth processing. Everything
     * else is accepted, <em>including</em> malformed dates and unknown product codes — judging those
     * is the module's job, and a {@code 400} would rob it of the chance to say which field was
     * wrong.</p>
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> processApplication(
            @Valid @RequestBody ApplicationRequest request) {
        applications.processApplicationAsync(request);
        return ResponseEntity.accepted().body(ack(request));
    }

    /**
     * The {@code 202} body — the shape in {@code api-contract.md} §2, spelled out here rather than
     * in a record of its own. Three of its four fields are a constant or an echo of the request,
     * the orchestrator throws the body away (it dispatches with {@code toBodilessEntity}), and the
     * sibling acknowledgements in the orchestrator and the sidecar are inline maps too.
     * {@code ApplicationControllerTest} pins all four fields.
     *
     * <p><b>{@code LinkedHashMap}, not {@code Map.of}.</b> {@code Map.of} throws on a null value,
     * and {@code command} is not a validated field — an envelope without one is legal and would
     * turn into a {@code 500}. This keeps field order for readability and serialises an absent
     * command as JSON {@code null}, which is what the record it replaced did.</p>
     */
    private Map<String, Object> ack(ApplicationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "in-progress");
        body.put("applicationId", request.applicationId());
        body.put("serviceId", serviceId);
        body.put("command", request.command());
        return body;
    }

    /**
     * Everything this module has answered, newest first. Read by this module's own UI; the
     * orchestrator never calls it.
     */
    @GetMapping
    public List<DemoShowcaseView> list() {
        return applications.findAll();
    }
}
