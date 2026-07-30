package com.neobank.module.integrations.idprovider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * <h2>Stop calling a provider that is clearly down.</h2>
 *
 * <p>The retry ladder answers "this one call failed, try again". It has no memory, so during a
 * one-hour outage every single application still pays three timeouts — 2 s + 1 s + 2 s + 2 s + 1 s
 * + 2 s ≈ ten seconds of worker thread, per applicant, to learn the same fact the last hundred
 * applicants already established. That is a retry storm: it makes the module slow, and it keeps
 * hammering a provider that is trying to recover.</p>
 *
 * <p>A breaker remembers. After enough consecutive failures it stops calling entirely and fails
 * immediately, then lets one call through after a cooling-off period to find out whether the
 * provider is back.</p>
 *
 * <pre>
 *   CLOSED ──── N consecutive failures ────▶ OPEN
 *     ▲                                        │
 *     │                                 cooldown elapses
 *     │                                        ▼
 *     └──── trial call succeeds ───────── HALF_OPEN ──── trial call fails ──▶ OPEN
 * </pre>
 *
 * <h3>Two decisions worth understanding</h3>
 *
 * <p><b>Consecutive, not cumulative.</b> A success resets the counter to zero. A provider that has
 * failed five times today but works right now is a working provider; counting failures for ever
 * would trip the breaker on a healthy system that has simply been up a long time.</p>
 *
 * <p><b>The clock is injected.</b> Every transition here is a function of time, and the alternative
 * to a {@link Clock} is a test that sleeps for the cooling-off period — which means either a
 * 30-second test or a 50 ms cooldown that proves nothing about the real one. With a mutable clock
 * the whole state machine is exercised in microseconds at its production settings.</p>
 *
 * <p>Hand-written rather than resilience4j on purpose: this is roughly sixty lines that a student
 * can read, explain and step through in a debugger, and every line of it is code we can be asked
 * about. A library would move the behaviour into configuration nobody can walk through.</p>
 *
 * <p>Instances are per agency — the national agency being down says nothing about the tax agency,
 * and a shared breaker would take the fallback out at exactly the moment it is needed. Methods are
 * synchronized because several worker threads decide applications at once.</p>
 */
public class CircuitBreaker {

    /** What the breaker is doing right now. */
    public enum State {
        /** Calling normally. */
        CLOSED,
        /** Not calling at all — failing fast until the cooling-off period elapses. */
        OPEN,
        /** Letting exactly one call through to find out whether the provider is back. */
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final Duration cooldown;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;
    private Instant lastTransitionAt;

    public CircuitBreaker(String name, int failureThreshold, Duration cooldown, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1");
        }
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.cooldown = cooldown;
        this.clock = clock;
        this.lastTransitionAt = clock.instant();
    }

    /**
     * May we call the provider?
     *
     * <p><b>Not a pure query — it can transition OPEN to HALF_OPEN.</b> That is deliberate: the
     * cooling-off period ends when someone asks, so there is no background timer to schedule, shut
     * down, or leak. The cost is that this must be called exactly once per attempt, immediately
     * before the attempt.</p>
     */
    public synchronized boolean allowsRequest() {
        if (state == State.OPEN && clock.instant().isAfter(openedAt.plus(cooldown))) {
            transitionTo(State.HALF_OPEN);
        }
        return state != State.OPEN;
    }

    /** The provider answered. Back to normal, and the failure count starts again from zero. */
    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        if (state != State.CLOSED) {
            transitionTo(State.CLOSED);
        }
    }

    /**
     * The provider did not answer.
     *
     * <p>A failure while HALF_OPEN re-opens the breaker immediately, without waiting for the
     * threshold again — the trial call was the test, and it failed. Waiting for another N failures
     * would mean N more real applications paying the full timeout to re-learn it.</p>
     */
    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            openedAt = clock.instant();
            transitionTo(State.OPEN);
        }
    }

    private void transitionTo(State next) {
        if (state != next) {
            state = next;
            lastTransitionAt = clock.instant();
        }
    }

    public synchronized State state() {
        return state;
    }

    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }

    public synchronized Instant lastTransitionAt() {
        return lastTransitionAt;
    }

    /** When the breaker will next let a trial call through, or null when it is not open. */
    public synchronized Instant retryAt() {
        return state == State.OPEN ? openedAt.plus(cooldown) : null;
    }

    public String name() {
        return name;
    }
}
