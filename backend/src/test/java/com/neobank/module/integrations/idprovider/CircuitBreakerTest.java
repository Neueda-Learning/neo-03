package com.neobank.module.integrations.idprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every transition, at production settings, in microseconds.
 *
 * <p>That is what the injected clock buys. The alternative is either a test that really sleeps for
 * the 30-second cooldown, or a cooldown shrunk to 50 ms for testing — which proves that a 50 ms
 * cooldown works and says nothing about the one that ships.</p>
 */
class CircuitBreakerTest {

    /** A clock the test moves by hand. */
    private static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-07-29T10:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private MovableClock clock;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        breaker = new CircuitBreaker("NATIONAL", 5, Duration.ofSeconds(30), clock);
    }

    @Test
    @DisplayName("Starts closed and allows requests")
    void startsClosed() {
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsRequest()).isTrue();
    }

    @Test
    @DisplayName("Opens on the Nth consecutive failure, not the (N-1)th")
    void opensExactlyAtTheThreshold() {
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
            assertThat(breaker.state()).as("after %d failures", i + 1)
                    .isEqualTo(CircuitBreaker.State.CLOSED);
        }
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    @DisplayName("A success resets the count — CONSECUTIVE failures, not cumulative")
    void successResetsTheFailureCount() {
        // The distinction that matters: a provider that failed four times this morning and works
        // now is a working provider. Counting for ever would trip the breaker on a healthy system
        // purely for having been up a long time.
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
        }
        breaker.recordSuccess();
        assertThat(breaker.consecutiveFailures()).isZero();

        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Stays open for the whole cooldown, then goes half-open")
    void staysOpenUntilTheCooldownElapses() {
        trip();

        clock.advance(Duration.ofSeconds(29));
        assertThat(breaker.allowsRequest()).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        clock.advance(Duration.ofSeconds(2));
        assertThat(breaker.allowsRequest()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    @DisplayName("A successful trial call closes the circuit")
    void aSuccessfulTrialCloses() {
        trip();
        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.allowsRequest()).isTrue();

        breaker.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.consecutiveFailures()).isZero();
        assertThat(breaker.retryAt()).isNull();
    }

    @Test
    @DisplayName("A failed trial re-opens IMMEDIATELY — it does not need another five failures")
    void aFailedTrialReopensAtOnce() {
        trip();
        clock.advance(Duration.ofSeconds(31));
        breaker.allowsRequest();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        breaker.recordFailure();

        // The trial call WAS the test, and it failed. Waiting for another five would mean five
        // more real applications each paying the full timeout to re-learn the same fact.
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsRequest()).isFalse();
    }

    @Test
    @DisplayName("The cooldown restarts from the re-open, not from the first trip")
    void theCooldownRestartsOnReopen() {
        trip();
        clock.advance(Duration.ofSeconds(31));
        breaker.allowsRequest();
        breaker.recordFailure();

        clock.advance(Duration.ofSeconds(29));
        assertThat(breaker.allowsRequest()).isFalse();
        clock.advance(Duration.ofSeconds(2));
        assertThat(breaker.allowsRequest()).isTrue();
    }

    @Test
    @DisplayName("retryAt says when it will try again, and is null when it is not open")
    void retryAtIsOnlySetWhileOpen() {
        assertThat(breaker.retryAt()).isNull();
        trip();
        assertThat(breaker.retryAt()).isEqualTo(clock.instant().plusSeconds(30));
    }

    @Test
    @DisplayName("A threshold below one is refused at construction")
    void thresholdMustBeAtLeastOne() {
        assertThatThrownBy(() -> new CircuitBreaker("X", 0, Duration.ofSeconds(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void trip() {
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure();
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}
