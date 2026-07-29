package com.neobank.module.integrations.idprovider;

/**
 * What one call to an identity source actually did.
 *
 * <p>Four values, not two, because "it did not work" is three different facts to an operator
 * looking at a stuck case: the provider was slow, the provider was broken, or the provider said no.
 * Each one has a different owner and a different fix, and collapsing them into a single FAILED
 * throws that away.</p>
 *
 * <p>Every one of these except {@link #ANSWERED} is retryable — that is the whole reason for
 * classifying: an outage is never the applicant's fault.</p>
 */
public enum AttemptResult {

    /** The source answered with a confidence score. The only non-retryable outcome. */
    ANSWERED,

    /** No answer within the timeout budget. The source is up but too slow to be useful. */
    TIMEOUT,

    /** The source answered, with a 5xx. It is broken, and it knows it. */
    ERROR,

    /** Nothing was listening, or the connection was refused outright. */
    REFUSED,

    /**
     * We did not call at all — the circuit breaker was open.
     *
     * <p>Recorded rather than hidden, because "we chose not to try" and "we tried and it failed"
     * look identical on a case otherwise, and only one of them means the provider is definitely
     * still down.</p>
     */
    SHORT_CIRCUITED
}
