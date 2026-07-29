package com.neobank.module.integrations.orchestrator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.neobank.module.model.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * <h2>The one class in this module that must not change shape.</h2>
 *
 * <p>Everything else here is ours to redesign. This is the wire: the orchestrator is deployed
 * separately, will not be rebuilt to match us, and matches the step on {@code serviceId} with a
 * plain {@code equals} — so a wrong verb, a wrong path, a fourth field or a mis-cased id does not
 * fail loudly, it is silently ignored and the journey dies on a 30-second timeout with nothing to
 * explain it.</p>
 *
 * <p>It had no tests at all. These pin the four things that can silently break it.</p>
 */
class OrchestratorClientTest {

    private MockRestServiceServer server;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OrchestratorClient(builder.build(), "neo03", "http://orchestrator:8080");
    }

    @Test
    @DisplayName("PUT to /api/v1/applications/{id} with exactly three fields")
    void reportsWithTheExactContractShape() {
        server.expect(requestTo("http://orchestrator:8080/api/v1/applications/SIM-01"))
                // PUT, not POST: this updates the status of a resource the orchestrator already
                // owns, which is also why the id is in the path and not in the body.
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.serviceId").value("neo03"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.comment").value("KYC_VERIFIED · identity confirmed"))
                // Three fields, no more. An applicationId in the body is ignored by the
                // orchestrator today, but the contract says three and this is the contract test.
                .andExpect(jsonPath("$.applicationId").doesNotExist())
                .andRespond(withSuccess());

        client.applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
                "KYC_VERIFIED · identity confirmed");

        server.verify();
    }

    @Test
    @DisplayName("The status is the enum name, uppercase — a typo cannot reach the wire")
    void everyDecisionIsSentAsItsUppercaseName() {
        for (Decision decision : Decision.values()) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer perCall = MockRestServiceServer.bindTo(builder).build();
            perCall.expect(requestTo("http://orchestrator:8080/api/v1/applications/SIM-01"))
                    .andExpect(jsonPath("$.status").value(decision.name()))
                    .andRespond(withSuccess());

            new OrchestratorClient(builder.build(), "neo03", "http://orchestrator:8080")
                    .applicationStatusUpdate("SIM-01", decision, "why");

            perCall.verify();
        }
    }

    @Test
    @DisplayName("serviceId is neo03 — no hyphen, matching the orchestrator's registry")
    void serviceIdIsTheRegistryFormNotTheRepoName() {
        // The repo is neo-03 and the serviceId is neo03. The orchestrator compares with .equals()
        // and silently ignores a mismatch, so the hyphen is a one-character way to make this
        // module invisible to the journey while every one of its own screens looks fine.
        server.expect(requestTo("http://orchestrator:8080/api/v1/applications/SIM-01"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"neo03\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("neo-03"))))
                .andRespond(withSuccess());

        client.applicationStatusUpdate("SIM-01", Decision.ACCEPTED, "ok");

        server.verify();
    }

    @Test
    @DisplayName("An unreachable orchestrator is logged, never thrown")
    void aFailedReportDoesNotThrow() {
        // The decision is already committed to our own database. Re-throwing here would roll
        // nothing back and would only kill the worker thread — and the orchestrator's own timeout
        // sweeper already handles a step that never reports.
        server.expect(requestTo("http://orchestrator:8080/api/v1/applications/SIM-01"))
                .andRespond(withServerError());

        assertThatCode(() -> client.applicationStatusUpdate("SIM-01", Decision.ACCEPTED, "ok"))
                .doesNotThrowAnyException();
    }
}
