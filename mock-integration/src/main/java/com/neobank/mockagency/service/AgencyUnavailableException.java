package com.neobank.mockagency.service;

/**
 * The agency refused to answer — kill switch, injected failure rate, or the corpus's
 * "always fails" document. Becomes a {@code 503} with a readable reason.
 *
 * <p>A 503 and not a 500: the difference matters to the caller, which classifies a 5xx as a
 * retryable outage rather than a bug, and the retry ladder exists for exactly this.</p>
 */
public class AgencyUnavailableException extends RuntimeException {

    public AgencyUnavailableException(String message) {
        super(message);
    }
}
