package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.idprovider.Agency;
import com.neobank.module.integrations.idprovider.AttemptResult;
import com.neobank.module.integrations.idprovider.CircuitBreaker;
import com.neobank.module.integrations.idprovider.IdVerificationClient;
import com.neobank.module.integrations.idprovider.ProviderAnswer;
import com.neobank.module.integrations.idprovider.ProviderUnavailableException;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.model.ThirdPartyAttempt;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How hard the module tries, and who it tries next.
 *
 * <p>The backoff assertions are on the REAL numbers — 1000 ms then 2000 ms — and the suite still
 * runs in milliseconds, because {@link Sleeper} is a seam and the fake simply records what it was
 * asked to wait for. A test that shrinks the backoff to make itself fast is a test that proves the
 * shrunken value works.</p>
 */
class ProviderGatewayTest {

    /** Records what it was asked to wait for, and waits for none of it. */
    private static class RecordingSleeper implements Sleeper {
        final List<Long> waits = new ArrayList<>();

        @Override
        public void sleep(long millis) {
            waits.add(millis);
        }
    }

    private static final Application APPLICATION = new Application(
            "SIM-01", "MOBILE_APP", "2026-07-25T09:14:00Z",
            new Application.Applicant("Maria Nowak", "1996-04-11", null, null, "PL", "PL",
                    null, null, null, null, null),
            new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
            null, null, null, null, null);

    private IdVerificationClient client;
    private RecordingSleeper sleeper;
    private ProviderGateway gateway;

    @BeforeEach
    void setUp() {
        client = mock(IdVerificationClient.class);
        sleeper = new RecordingSleeper();
        gateway = newGateway(5);
    }

    private ProviderGateway newGateway(int breakerThreshold) {
        return new ProviderGateway(client, sleeper,
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC),
                3, new long[] {1000L, 2000L}, breakerThreshold, 30_000L);
    }

    private static ProviderAnswer answer(int confidence) {
        return new ProviderAnswer("ref-1", "NATIONAL_IDENTITY_AGENCY", confidence,
                List.of(new ProviderAnswer.Check("documentGenuine", true)));
    }

    private static ProviderUnavailableException down() {
        return new ProviderUnavailableException(AttemptResult.TIMEOUT, "timed out");
    }

    @Test
    @DisplayName("An answer first time ends it: one attempt, no waiting, no fallback")
    void oneAttemptWhenTheProviderAnswers() {
        when(client.verify(eq(Agency.NATIONAL), any())).thenReturn(answer(92));

        ProviderGateway.ProviderOutcome outcome = gateway.verify("kyc-1", APPLICATION);

        assertThat(outcome.answered()).isTrue();
        assertThat(outcome.answer().confidence()).isEqualTo(92);
        assertThat(outcome.agencyUsed()).isEqualTo(Agency.NATIONAL);
        assertThat(outcome.failedOver()).isFalse();
        assertThat(outcome.attempts()).hasSize(1);
        assertThat(sleeper.waits).isEmpty();
        verify(client, never()).verify(eq(Agency.TAX), any());
    }

    @Test
    @DisplayName("A LOW score is an answer — it is never retried")
    void aLowConfidenceIsNotRetried() {
        // Retrying a low score until it improves is not resilience, it is asking the same question
        // until you get the answer you want — and it bills three provider fees to do it.
        when(client.verify(eq(Agency.NATIONAL), any())).thenReturn(answer(12));

        ProviderGateway.ProviderOutcome outcome = gateway.verify("kyc-1", APPLICATION);

        assertThat(outcome.answered()).isTrue();
        assertThat(outcome.attempts()).hasSize(1);
        verify(client, times(1)).verify(any(), any());
    }

    @Test
    @DisplayName("The ladder waits 1000ms then 2000ms — the real numbers, asserted")
    void backoffIsOneSecondThenTwo() {
        when(client.verify(eq(Agency.NATIONAL), any())).thenThrow(down());
        when(client.verify(eq(Agency.TAX), any())).thenThrow(down());

        gateway.verify("kyc-1", APPLICATION);

        // Between the three primary attempts, and NOT after the last one: sleeping after the final
        // failure delays the answer by two seconds and changes nothing about it.
        assertThat(sleeper.waits).containsExactly(1000L, 2000L);
    }

    @Test
    @DisplayName("Three attempts on the primary, then exactly ONE on the fallback")
    void theFallbackGetsOneAttemptNotAnotherThree() {
        when(client.verify(eq(Agency.NATIONAL), any())).thenThrow(down());
        when(client.verify(eq(Agency.TAX), any())).thenThrow(down());

        ProviderGateway.ProviderOutcome outcome = gateway.verify("kyc-1", APPLICATION);

        verify(client, times(3)).verify(eq(Agency.NATIONAL), any());
        verify(client, times(1)).verify(eq(Agency.TAX), any());
        assertThat(outcome.answered()).isFalse();
        assertThat(outcome.attempts()).hasSize(4);
        assertThat(outcome.lastFailure()).isEqualTo(AttemptResult.TIMEOUT);
    }

    @Test
    @DisplayName("The fallback answering is a success, flagged as a failover")
    void failoverAnswersAndIsFlagged() {
        when(client.verify(eq(Agency.NATIONAL), any())).thenThrow(down());
        when(client.verify(eq(Agency.TAX), any())).thenReturn(answer(95));

        ProviderGateway.ProviderOutcome outcome = gateway.verify("kyc-1", APPLICATION);

        assertThat(outcome.answered()).isTrue();
        assertThat(outcome.agencyUsed()).isEqualTo(Agency.TAX);
        assertThat(outcome.failedOver()).isTrue();
        assertThat(outcome.attempts()).hasSize(4);
    }

    @Test
    @DisplayName("Every call becomes an attempt row, numbered in order, with its agency")
    void everyCallIsLoggedAsEvidence() {
        when(client.verify(eq(Agency.NATIONAL), any())).thenThrow(down());
        when(client.verify(eq(Agency.TAX), any())).thenReturn(answer(95));

        List<ThirdPartyAttempt> attempts = gateway.verify("kyc-1", APPLICATION).attempts();

        assertThat(attempts).extracting(ThirdPartyAttempt::getAttemptNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(attempts).extracting(ThirdPartyAttempt::getAgency)
                .containsExactly("NATIONAL", "NATIONAL", "NATIONAL", "TAX");
        assertThat(attempts).extracting(ThirdPartyAttempt::getResult)
                .containsExactly("TIMEOUT", "TIMEOUT", "TIMEOUT", "ANSWERED");
        assertThat(attempts).allSatisfy(attempt -> {
            assertThat(attempt.getKycId()).isEqualTo("kyc-1");
            // requested_at, not created_at: all four rows are saved together in one batch, so
            // created_at is identical across them and proves nothing about the backoff.
            assertThat(attempt.getRequestedAt()).isNotNull();
        });
        assertThat(attempts.getLast().getConfidence()).isEqualTo(95);
        assertThat(attempts.getLast().getProviderRef()).isEqualTo("ref-1");
    }

    @Test
    @DisplayName("The classified failure reaches the attempt row — timeout, error and refused differ")
    void failureKindsAreDistinguishedOnTheRow() {
        when(client.verify(eq(Agency.NATIONAL), any()))
                .thenThrow(new ProviderUnavailableException(AttemptResult.TIMEOUT, "slow"))
                .thenThrow(new ProviderUnavailableException(AttemptResult.ERROR, "500"))
                .thenThrow(new ProviderUnavailableException(AttemptResult.REFUSED, "no listener"));
        when(client.verify(eq(Agency.TAX), any())).thenReturn(answer(95));

        List<ThirdPartyAttempt> attempts = gateway.verify("kyc-1", APPLICATION).attempts();

        assertThat(attempts).extracting(ThirdPartyAttempt::getResult)
                .startsWith("TIMEOUT", "ERROR", "REFUSED");
    }

    @Test
    @DisplayName("The breaker cuts the ladder short mid-application, then stops calling entirely")
    void anOpenBreakerMakesNoCallsAtAll() {
        ProviderGateway tightGateway = newGateway(2);
        when(client.verify(any(), any())).thenThrow(down());

        // First application, with the breakers set to trip at 2 rather than 5:
        //   national #1 fails (1), national #2 fails (2 -> OPEN), national #3 SHORT-CIRCUITED,
        //   tax #1 fails (1, still closed).
        // So THREE real calls, not four. The breaker does not wait politely for the ladder to
        // finish — it stops it as soon as it has seen enough, which is the point of having it.
        ProviderGateway.ProviderOutcome first = tightGateway.verify("kyc-1", APPLICATION);
        assertThat(org.mockito.Mockito.mockingDetails(client).getInvocations()).hasSize(3);
        assertThat(first.attempts()).extracting(ThirdPartyAttempt::getResult)
                .containsExactly("TIMEOUT", "TIMEOUT", "SHORT_CIRCUITED", "TIMEOUT");

        // Second application: national is open, and tax reaches 2 failures and opens too.
        ProviderGateway.ProviderOutcome second = tightGateway.verify("kyc-2", APPLICATION);
        assertThat(org.mockito.Mockito.mockingDetails(client).getInvocations()).hasSize(4);

        // Third application: both breakers open — ZERO calls go out. This is the whole point:
        // during an outage every later applicant costs no network time at all instead of ten
        // seconds of held worker thread to re-learn what we already know.
        ProviderGateway.ProviderOutcome third = tightGateway.verify("kyc-3", APPLICATION);

        assertThat(org.mockito.Mockito.mockingDetails(client).getInvocations()).hasSize(4);
        assertThat(third.answered()).isFalse();
        assertThat(third.attempts()).allSatisfy(attempt ->
                assertThat(attempt.getResult()).isEqualTo("SHORT_CIRCUITED"));
        assertThat(third.lastFailure()).isEqualTo(AttemptResult.SHORT_CIRCUITED);
        assertThat(second.answered()).isFalse();
    }

    @Test
    @DisplayName("A short-circuited attempt is still recorded, not hidden")
    void shortCircuitedAttemptsAreStillLogged() {
        // "We chose not to try" and "we tried and it failed" look identical on a parked case
        // otherwise, and only one of them is evidence the provider is definitely still down.
        ProviderGateway tightGateway = newGateway(1);
        when(client.verify(any(), any())).thenThrow(down());
        tightGateway.verify("kyc-1", APPLICATION);

        List<ThirdPartyAttempt> attempts = tightGateway.verify("kyc-2", APPLICATION).attempts();

        assertThat(attempts).isNotEmpty();
        assertThat(attempts).allSatisfy(attempt -> {
            assertThat(attempt.getResult()).isEqualTo("SHORT_CIRCUITED");
            assertThat(attempt.getComment()).contains("breaker open");
        });
    }

    @Test
    @DisplayName("Each agency has its OWN breaker — the primary failing must not take out the fallback")
    void breakersArePerAgency() {
        ProviderGateway tightGateway = newGateway(3);
        when(client.verify(eq(Agency.NATIONAL), any())).thenThrow(down());
        when(client.verify(eq(Agency.TAX), any())).thenReturn(answer(95));

        // Two applications: the national breaker sees 3 failures and opens; the tax breaker sees
        // only successes. A shared breaker would open on the primary's failures and remove the
        // fallback at exactly the moment it is needed.
        tightGateway.verify("kyc-1", APPLICATION);
        ProviderGateway.ProviderOutcome second = tightGateway.verify("kyc-2", APPLICATION);

        assertThat(tightGateway.breakers().get(Agency.NATIONAL).state())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(tightGateway.breakers().get(Agency.TAX).state())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(second.answered()).isTrue();
        assertThat(second.agencyUsed()).isEqualTo(Agency.TAX);
    }

    @Test
    @DisplayName("A ladder longer than the backoff list reuses the last wait rather than crashing")
    void backoffListShorterThanTheLadderIsSafe() {
        // Raising max-attempts without touching backoff-ms should give a longer ladder, not a
        // startup crash or an unbounded exponential wait.
        ProviderGateway longLadder = new ProviderGateway(client, sleeper,
                Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC),
                5, new long[] {1000L, 2000L}, 99, 30_000L);
        when(client.verify(any(), any())).thenThrow(down());

        longLadder.verify("kyc-1", APPLICATION);

        assertThat(sleeper.waits).containsExactly(1000L, 2000L, 2000L, 2000L);
    }

    @Test
    @DisplayName("An open breaker waits for nothing — a skipped attempt has nothing to back off from")
    void noBackoffAfterAShortCircuitedAttempt() {
        // The breaker exists so an outage costs nothing. Sleeping between attempts that never left
        // the module spends the full 1s + 2s of a worker thread on an application whose calls were
        // all skipped, and delays the failover to the fallback by the same three seconds.
        ProviderGateway tightGateway = newGateway(1);   // opens after a single failure
        when(client.verify(any(), any())).thenThrow(down());

        tightGateway.verify("kyc-1", APPLICATION);      // trips both breakers
        sleeper.waits.clear();

        ProviderGateway.ProviderOutcome second = tightGateway.verify("kyc-2", APPLICATION);

        assertThat(second.attempts()).allSatisfy(attempt ->
                assertThat(attempt.getResult()).isEqualTo("SHORT_CIRCUITED"));
        assertThat(sleeper.waits)
                .as("nothing was called, so there is nothing to wait for")
                .isEmpty();
    }

    @Test
    @DisplayName("A ladder that half short-circuits waits only for the calls it really made")
    void backoffFollowsRealCallsOnly() {
        // The mixed case, and the one the operator screen showed: the first attempt is a real call
        // that fails and trips the breaker, so it earns its 1s. Attempts 2 and 3 are skipped and
        // earn nothing. Before the fix this waited 1s AND 2s.
        ProviderGateway tightGateway = newGateway(1);
        when(client.verify(any(), any())).thenThrow(down());

        tightGateway.verify("kyc-1", APPLICATION);

        assertThat(sleeper.waits).containsExactly(1000L);
    }
}
