package com.neobank.module.integrations.orchestrator;

import com.neobank.module.model.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The outbound half of the contract: telling the orchestrator what this module decided.
 *
 * <p>{@code ApplicationController} is the way in, this is the way out. Everything in the
 * {@code orchestrator} package is the wire; everything else in the module is local.</p>
 */
@Component
public class OrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

    private final RestClient http;
    private final String serviceId;
    private final String applicationsUrl;

    public OrchestratorClient(RestClient http,
                              @Value("${service.id:neo03}") String serviceId,
                              @Value("${service.orchestrator-url:http://localhost:9000}") String orchestratorUrl) {
        this.http = http;
        this.serviceId = serviceId;
        this.applicationsUrl = orchestratorUrl + "/api/v1/applications";
    }

    /**
     * Report the outcome: {@code PUT /api/v1/applications/{applicationId}}.
     *
     * <p>A {@code PUT} on the application, not a post to a mailbox — this is an update to the
     * status of something the orchestrator already has, which is why the id is in the URL and not
     * in the body.</p>
     *
     * <p><b>Failures are logged, never thrown.</b> The decision is already committed to our own
     * database, so re-throwing would roll nothing back and would only kill the worker thread. If
     * the orchestrator cannot be reached it treats the step as timed out — that is its job, not
     * ours.</p>
     */
    public void applicationStatusUpdate(String applicationId, Decision status, String comment) {
        ApplicationStatusUpdate body = new ApplicationStatusUpdate(serviceId, status.name(), comment);
        try {
            http.put()
                    .uri(applicationsUrl + "/" + applicationId)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("REPORTED {} -> {}", applicationId, status);
        } catch (Exception e) {
            log.warn("Status update to the orchestrator failed for {}: {} — its timeout sweeper "
                    + "will notice", applicationId, e.toString());
        }
    }
}
