package com.neobank.module.service;

import org.springframework.stereotype.Component;

/**
 * Waiting, as a seam.
 *
 * <p>One line of production code and it exists entirely for the tests — which is the right trade
 * here. The retry ladder's backoff is 1 s then 2 s, so a test that really waits takes three seconds
 * per case and the suite stops being something anyone runs. The usual dodge is to make the backoff
 * configurable and set it to 5 ms in tests, but then the test proves that a 5 ms backoff works and
 * says nothing about the 1 s one that ships.</p>
 *
 * <p>With this interface a fake records the durations it was asked to wait, and the test asserts
 * <b>1000 then 2000</b> — the real numbers, instantly.</p>
 */
@FunctionalInterface
public interface Sleeper {

    void sleep(long millis);

    /** The real thing. */
    @Component
    class ThreadSleeper implements Sleeper {

        @Override
        public void sleep(long millis) {
            if (millis <= 0) {
                return;
            }
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                // Restore the flag and stop. Swallowing an interrupt leaves a worker thread that
                // cannot be shut down, which turns a container stop into a 30-second kill.
                Thread.currentThread().interrupt();
                throw new IllegalStateException("provider retry interrupted", e);
            }
        }
    }
}
