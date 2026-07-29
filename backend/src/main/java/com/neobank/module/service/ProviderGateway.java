package com.neobank.module.service;

import com.neobank.module.integrations.idprovider.Agency;
import com.neobank.module.integrations.idprovider.AttemptResult;
import com.neobank.module.integrations.idprovider.CircuitBreaker;
import com.neobank.module.integrations.idprovider.IdVerificationClient;
import com.neobank.module.integrations.idprovider.ProviderAnswer;
import com.neobank.module.integrations.idprovider.ProviderUnavailableException;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.ThirdPartyAttempt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * <h2>How hard to try, and who to try next.</h2>
 *
 * <p>{@link IdVerificationClient} makes one call. This decides how many calls to make, how long to
 * wait between them, when to give up on the primary and ask the fallback, and when to stop calling
 * altogether. All of that is <b>policy</b>, and separating it from the call is what lets the policy
 * be tested without a socket.</p>
 *
 * <pre>
 *   national attempt 1
 *      └─ fail → wait 1s → attempt 2
 *                   └─ fail → wait 2s → attempt 3
 *                                └─ fail → TAX, ONE attempt, no budget of its own
 *                                              └─ fail → unavailable
 * </pre>
 *
 * <h3>Three rules that look arbitrary and are not</h3>
 *
 * <p><b>An answer ends the ladder, whatever it says.</b> A confidence of 12 is the provider doing
 * its job. Retrying a low score until it improves is not resilience, it is asking the same question
 * until you get the answer you want — and it would turn one rejected applicant into three provider
 * fees.</p>
 *
 * <p><b>The fallback gets one attempt, not another three.</b> It is reached only after the primary
 * has already cost ~5 s of a worker thread; giving it a full ladder would double the worst case for
 * every application during an outage. One call is enough to find out whether it is up.</p>
 *
 * <p><b>Waiting happens between attempts, never after the last one.</b> Sleeping after the final
 * failure delays the answer by two seconds and changes nothing about it — a real bug this ladder's
 * predecessor did not have and that is easy to reintroduce by moving the sleep to the top of the
 * loop.</p>
 */
@Service
public class ProviderGateway {

    private static final Logger log = LoggerFactory.getLogger(ProviderGateway.class);

    private final IdVerificationClient client;
    private final Sleeper sleeper;
    private final Clock clock;
    private final int maxAttempts;
    private final long[] backoffMillis;
    private final Map<Agency, CircuitBreaker> breakers = new EnumMap<>(Agency.class);

    public ProviderGateway(IdVerificationClient client,
                           Sleeper sleeper,
                           Clock clock,
                           @Value("${id-provider.max-attempts:3}") int maxAttempts,
                           @Value("${id-provider.backoff-ms:1000,2000}") long[] backoffMillis,
                           @Value("${id-provider.breaker.failure-threshold:5}") int failureThreshold,
                           @Value("${id-provider.breaker.cooldown-ms:30000}") long cooldownMillis) {
        this.client = client;
        this.sleeper = sleeper;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis.clone();
        for (Agency agency : Agency.values()) {
            // One breaker per agency: the primary being down says nothing about the fallback, and
            // a shared breaker would take the fallback out at exactly the moment it is needed.
            breakers.put(agency, new CircuitBreaker(
                    agency.name(), failureThreshold, Duration.ofMillis(cooldownMillis), clock));
        }
    }

    /**
     * Get an identity verdict for this application, or establish that nobody will give one.
     *
     * @param kycId the case these attempts belong to — every call becomes one attempt row, because
     *              the attempt log is the evidence that the ladder ran, not debris
     */
    public ProviderOutcome verify(String kycId, Application application) {
        List<ThirdPartyAttempt> attempts = new ArrayList<>();
        String documentType = application.identityDocument().type();

        // Agency.values() IS the failover chain, in declaration order. A third source is a new
        // enum constant, not an edit here.
        for (Agency agency : Agency.values()) {
            int budget = agency == Agency.NATIONAL ? maxAttempts : 1;

            for (int attempt = 1; attempt <= budget; attempt++) {
                Attempt outcome = callOnce(agency, application, kycId, attempts.size() + 1, documentType);
                attempts.add(outcome.row());

                if (outcome.answer() != null) {
                    boolean failedOver = agency != Agency.NATIONAL;
                    if (failedOver) {
                        log.info("failed over to {} — the primary's budget was spent", agency);
                    }
                    return new ProviderOutcome(outcome.answer(), agency, failedOver, attempts, null);
                }
                if (attempt < budget) {
                    sleeper.sleep(backoffFor(attempt));
                }
            }
        }

        AttemptResult lastResult = attempts.isEmpty()
                ? AttemptResult.SHORT_CIRCUITED
                : AttemptResult.valueOf(attempts.get(attempts.size() - 1).getResult());
        log.warn("no identity source answered after {} attempts — last result {}",
                attempts.size(), lastResult);
        return new ProviderOutcome(null, null, false, attempts, lastResult);
    }

    private Attempt callOnce(Agency agency, Application application, String kycId,
                             int attemptNumber, String documentType) {
        CircuitBreaker breaker = breakers.get(agency);
        Instant requestedAt = clock.instant();

        if (!breaker.allowsRequest()) {
            // Recorded, not hidden. "We chose not to try" and "we tried and it failed" look
            // identical on a parked case otherwise, and only one of them is evidence the provider
            // is still down.
            log.warn("{} not called — circuit breaker is OPEN until {}", agency, breaker.retryAt());
            return new Attempt(null, row(kycId, attemptNumber, agency, documentType,
                    AttemptResult.SHORT_CIRCUITED, null, requestedAt, 0L, null,
                    "breaker open — provider not called"));
        }

        try {
            ProviderAnswer answer = client.verify(agency, application);
            breaker.recordSuccess();
            return new Attempt(answer, row(kycId, attemptNumber, agency, documentType,
                    AttemptResult.ANSWERED, answer.confidence(), requestedAt,
                    elapsedMillis(requestedAt), answer.providerRef(),
                    "answered with confidence " + answer.confidence()));

        } catch (ProviderUnavailableException e) {
            breaker.recordFailure();
            return new Attempt(null, row(kycId, attemptNumber, agency, documentType,
                    e.result(), null, requestedAt, elapsedMillis(requestedAt), null,
                    e.getMessage()));
        }
    }

    /**
     * The gap before the next attempt.
     *
     * <p>Reading past the end of the configured list reuses the last value rather than throwing, so
     * raising {@code max-attempts} to 5 without touching {@code backoff-ms} gives 1s, 2s, 2s, 2s —
     * a longer ladder, not a startup crash. Exponential growth without a ceiling is how a retry
     * budget turns into a minute of held worker thread.</p>
     */
    private long backoffFor(int completedAttempts) {
        if (backoffMillis.length == 0) {
            return 0;
        }
        int index = Math.min(completedAttempts - 1, backoffMillis.length - 1);
        return backoffMillis[index];
    }

    private long elapsedMillis(Instant from) {
        return Duration.between(from, clock.instant()).toMillis();
    }

    private ThirdPartyAttempt row(String kycId, int attemptNumber, Agency agency,
                                  String documentType, AttemptResult result, Integer confidence,
                                  Instant requestedAt, long latencyMs, String providerRef,
                                  String comment) {
        return new ThirdPartyAttempt(UUID.randomUUID().toString(), kycId, attemptNumber,
                documentType, result.name(), confidence, comment,
                agency.name(), requestedAt, (int) latencyMs, providerRef);
    }

    /** Current breaker state per agency — what {@code GET /api/v1/provider/health} reports. */
    public Map<Agency, CircuitBreaker> breakers() {
        return Map.copyOf(breakers);
    }

    private record Attempt(ProviderAnswer answer, ThirdPartyAttempt row) {
    }

    /**
     * What the ladder established.
     *
     * @param answer      the verdict, or {@code null} when nobody answered
     * @param agencyUsed  which source answered, or {@code null}
     * @param failedOver  true when the fallback is what answered — the caller turns this into
     *                    {@code KYC_FAILED_OVER_TO_SECONDARY}
     * @param attempts    one row per call, in order. Its SIZE is the proof: 0 means the provider
     *                    was never needed, 1 means it answered first time, 4 means the whole chain
     *                    was walked
     * @param lastFailure how the final attempt failed, when none answered
     */
    public record ProviderOutcome(ProviderAnswer answer,
                                  Agency agencyUsed,
                                  boolean failedOver,
                                  List<ThirdPartyAttempt> attempts,
                                  AttemptResult lastFailure) {

        public boolean answered() {
            return answer != null;
        }
    }
}
