package com.neobank.module.integrations.idprovider;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * <h2>Driving the mocked agencies from this module's own screen.</h2>
 *
 * <p>The mock has a control page of its own, and on a laptop that is the easiest way to reach it.
 * <b>On AWS it is unreachable.</b> The mock runs as a third container inside this service's ECS
 * task with no target group and no listener rule — deliberately, since nothing outside the task
 * should be able to configure a provider — so the only thing that can talk to it is the backend
 * sitting beside it on {@code 127.0.0.1:8081}. Without this proxy the dials simply do not exist in
 * a deployed environment.</p>
 *
 * <p>This is UC-05's Provider Control Panel, and it is <b>not</b> the back door the module rules
 * forbid: it configures a mocked external dependency, it cannot set a decision, and every
 * application still travels the full path — dispatch, ladder, callback — whatever it is set to.</p>
 *
 * <p>Read-and-write against a mock is also the reason this lives apart from
 * {@link IdVerificationClient}: that class is the business call and must stay ignorant of the fact
 * that its provider happens to be one we can reconfigure.</p>
 */
@Component
public class ProviderAdminClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderAdminClient.class);

    private final RestClient http;
    private final String baseUrl;

    public ProviderAdminClient(@Qualifier("idProviderRestClient") RestClient http,
                               @Value("${id-provider.base-url}") String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Both agencies' current dials, exactly as the mock reports them. */
    public Map<String, Object> readConfig() {
        return http.get()
                .uri(baseUrl + "/api/v1/admin/config")
                .retrieve()
                .body(Map.class);
    }

    /**
     * Apply one preset to BOTH agencies, or to the primary alone.
     *
     * @param primaryOnly the failover demo. Setting only the National Identity Agency down is what
     *                    makes the fallback answer; setting both is the outage.
     */
    public void apply(ProviderPreset preset, boolean primaryOnly) {
        put(Agency.NATIONAL, preset.forPrimary());
        if (!primaryOnly) {
            put(Agency.TAX, preset.forFallback());
        } else {
            // Leave the fallback healthy, explicitly — otherwise the previous preset's settings
            // linger on it and "primary down" quietly becomes "still broken from last time".
            put(Agency.TAX, ProviderPreset.NORMAL.forFallback());
        }
        log.info("provider preset applied — {}{}", preset, primaryOnly ? " (primary only)" : "");
    }

    private void put(Agency agency, Map<String, Object> body) {
        http.put()
                .uri(baseUrl + "/api/v1/admin/config/" + agency.slug())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * The presets the operator screen offers, each one a set of the mock's dials.
     *
     * <p>Presets rather than raw dials because the question an operator has is "show me what
     * happens when everything is rejected", not "set failureRatePct to 60". The dials remain
     * available on the mock's own page for anyone who wants them.</p>
     */
    public enum ProviderPreset {

        /** The document decides, deterministically. The only state a checkpoint is written against. */
        NORMAL("NORMAL", 0, false),

        /** Every applicant verifies. */
        ALL_PASS("ALL_PASS", 0, false),

        /** Every applicant parks for a human. */
        ALL_REVIEW("ALL_REVIEW", 0, false),

        /** Every applicant is refused. */
        ALL_FAIL("ALL_FAIL", 0, false),

        /**
         * Answers roughly half the time. Enough consecutive failures arrive to trip the circuit
         * breaker, then a success closes it again — so the breaker can be watched opening and
         * recovering rather than described.
         */
        FLAKY("NORMAL", 60, false),

        /** Refuses everything. With {@code primaryOnly} this is the failover demo. */
        DOWN("NORMAL", 0, true);

        private final String answerMode;
        private final int failureRatePct;
        private final boolean killSwitch;

        ProviderPreset(String answerMode, int failureRatePct, boolean killSwitch) {
            this.answerMode = answerMode;
            this.failureRatePct = failureRatePct;
            this.killSwitch = killSwitch;
        }

        Map<String, Object> forPrimary() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("latencyMs", 0);
            body.put("failureRatePct", failureRatePct);
            body.put("killSwitch", killSwitch);
            body.put("answerMode", answerMode);
            return body;
        }

        /** The fallback answers the same way — otherwise a failover would change the outcome. */
        Map<String, Object> forFallback() {
            return forPrimary();
        }
    }
}
