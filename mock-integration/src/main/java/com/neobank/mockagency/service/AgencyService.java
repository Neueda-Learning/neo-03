package com.neobank.mockagency.service;

import com.neobank.mockagency.dto.AgencyConfig;
import com.neobank.mockagency.dto.VerificationRequest;
import com.neobank.mockagency.dto.VerificationResponse;
import com.neobank.mockagency.dto.VerificationResponse.Check;
import com.neobank.mockagency.model.Agency;
import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Answers on behalf of an agency, or misbehaves on request.
 *
 * <p>The order of the gates is deliberate and is the service's whole behaviour:</p>
 *
 * <ol>
 *   <li><b>Kill switch</b> — refuse immediately. No latency, because an outage is not slow, it is
 *       absent.</li>
 *   <li><b>Latency</b> — applied BEFORE the failure roll, so a slow-and-failing agency really does
 *       cost the caller its full timeout before it fails. Applying it after would let the caller
 *       fail fast on a call that was supposed to hang.</li>
 *   <li><b>Injected failure rate</b> — a roll of the dice, and the ONLY randomness in this service.
 *       It is opt-in, defaults to zero, and never touches the confidence score.</li>
 *   <li><b>The corpus fixture</b> — {@code ZZ0000000} always fails, on both agencies, whatever the
 *       dials say. It is how you demonstrate an outage without turning anything on.</li>
 *   <li>Otherwise: answer, deterministically.</li>
 * </ol>
 */
@Service
public class AgencyService {

    private static final Logger log = LoggerFactory.getLogger(AgencyService.class);

    private final ConfidenceBook confidenceBook;
    private final Clock clock;

    /**
     * The state {@code reset()} restores. Read from configuration rather than hard-coded so a stack
     * can be booted already broken — {@code AGENCY_KILL_SWITCH=true docker compose up} — without
     * anyone having to remember to turn a dial after every restart.
     */
    private final AgencyConfig defaults;

    /** Live dials. Written by the admin API, read on every call. */
    private final Map<Agency, AgencyConfig> configs = new ConcurrentHashMap<>();

    /** How many calls each agency has answered or refused — the control page's counter. */
    private final Map<Agency, AtomicLong> calls = new ConcurrentHashMap<>();

    public AgencyService(ConfidenceBook confidenceBook,
                         Clock clock,
                         @Value("${agency.default-latency-ms:0}") int defaultLatencyMs,
                         @Value("${agency.default-failure-rate-pct:0}") int defaultFailureRatePct,
                         @Value("${agency.default-kill-switch:false}") boolean defaultKillSwitch) {
        this.confidenceBook = confidenceBook;
        this.clock = clock;
        this.defaults = new AgencyConfig(defaultLatencyMs, defaultFailureRatePct, defaultKillSwitch);
        reset();
    }

    /**
     * Run a verification.
     *
     * @throws AgencyUnavailableException when the agency is refusing — the caller sees a 503
     */
    public VerificationResponse verify(Agency agency, VerificationRequest request) {
        calls.computeIfAbsent(agency, a -> new AtomicLong()).incrementAndGet();
        AgencyConfig config = configFor(agency);
        String documentId = request.document().documentId();

        if (Boolean.TRUE.equals(config.killSwitch())) {
            // NOTE what is NOT in this message: the document id. Nothing in this service logs it
            // or returns it, because the bank's own rule is that it is sent to the provider and
            // appears nowhere else. A mock that leaks it teaches the wrong habit.
            log.warn("{} refused a check — kill switch is on", agency);
            throw new AgencyUnavailableException(agency + " is not accepting checks (kill switch)");
        }

        sleep(config.latencyMs());

        if (config.failureRatePct() > 0
                && ThreadLocalRandom.current().nextInt(100) < config.failureRatePct()) {
            log.warn("{} dropped a check — injected failure rate {}%", agency, config.failureRatePct());
            throw new AgencyUnavailableException(agency + " could not complete the check");
        }

        if (confidenceBook.alwaysFails(documentId)) {
            log.warn("{} could not complete a check — the register did not respond", agency);
            throw new AgencyUnavailableException(agency + " could not reach the identity register");
        }

        int confidence = confidenceBook.confidenceFor(documentId);
        boolean genuine = confidenceBook.genuineFor(documentId);

        log.info("{} answered a {} check — confidence {}", agency, request.document().type(), confidence);

        return new VerificationResponse(
                agency.refPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8),
                agency,
                confidence,
                checksFor(agency, request, confidence, genuine),
                clock.instant());
    }

    /**
     * What each agency actually looked at.
     *
     * <p>The National Identity Agency holds the document registers, so it reports four checks. The
     * Tax Agency reports three — it can confirm that a name, a date of birth and an address belong
     * together, because it bills that person, but it has never seen the passport. Dropping
     * {@code documentGenuine} is what makes the two sources genuinely different rather than two
     * copies of one; the caller's forgery rule simply has nothing to read after a failover, which
     * is a real consequence of using a weaker source.</p>
     */
    private List<Check> checksFor(Agency agency, VerificationRequest request,
                                  int confidence, boolean genuine) {
        boolean addressKnown = request.address() != null && request.address().country() != null;
        List<Check> shared = List.of(
                new Check("nameMatched", confidence > 60),
                new Check("dobConsistent", confidence > 60),
                new Check("addressConfirmed", addressKnown && confidence > 60));

        if (agency == Agency.TAX_AGENCY) {
            return shared;
        }
        List<Check> all = new java.util.ArrayList<>();
        all.add(new Check("documentGenuine", genuine));
        all.addAll(shared);
        return List.copyOf(all);
    }

    /** Both agencies' current dials, in a stable order for the control page. */
    public Map<Agency, AgencyConfig> allConfigs() {
        Map<Agency, AgencyConfig> snapshot = new EnumMap<>(Agency.class);
        for (Agency agency : Agency.values()) {
            snapshot.put(agency, configFor(agency));
        }
        return snapshot;
    }

    public AgencyConfig configFor(Agency agency) {
        return configs.getOrDefault(agency, defaults);
    }

    public AgencyConfig updateConfig(Agency agency, AgencyConfig config) {
        configs.put(agency, config);
        log.info("{} dials set — latency {}ms, failureRate {}%, killSwitch {}",
                agency, config.latencyMs(), config.failureRatePct(), config.killSwitch());
        return config;
    }

    /** Back to the configured starting position. The demo has to be reversible live. */
    public void reset() {
        for (Agency agency : Agency.values()) {
            configs.put(agency, defaults);
            calls.put(agency, new AtomicLong());
        }
        log.info("all agency dials reset — latency {}ms, failureRate {}%, killSwitch {}",
                defaults.latencyMs(), defaults.failureRatePct(), defaults.killSwitch());
    }

    /** Call counts per agency — how the control page shows a failover actually happening. */
    public Map<String, Long> callCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Agency agency : Agency.values()) {
            counts.put(agency.slug(), calls.getOrDefault(agency, new AtomicLong()).get());
        }
        return counts;
    }

    private void sleep(int millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgencyUnavailableException("check interrupted");
        }
    }
}
