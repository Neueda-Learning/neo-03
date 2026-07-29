package com.neobank.module.service;

/**
 * <h2>The locked reason codes for this module's domain.</h2>
 *
 * <p>Six, from the system's {@code api-contract.md} reason-code registry. <b>Do not invent
 * more.</b> A code nobody else recognises is worse than no code: the operator console, the
 * analytics module and the regulator's export all read this vocabulary, and one team's private
 * spelling silently disappears from every report that groups by it.</p>
 *
 * <h3>Why these are strings inside {@code comment} rather than a field of their own</h3>
 *
 * <p>The wire this module reports on is {@code PUT /api/v1/applications/{id}} with exactly three
 * fields — {@code serviceId}, {@code status}, {@code comment}. There is no {@code reasons[]} array
 * to put a code in, and the orchestrator is deployed separately and will not be rebuilt to add one.
 * So the code rides at the front of {@code comment}, where it is greppable in logs and readable to
 * the human the message is written for.</p>
 */
public enum ReasonCode {

    /** The provider's confidence was at or above the accept threshold. */
    KYC_VERIFIED,

    /**
     * The pre-check fired: the document is out of date. <b>The provider is never called</b>, which
     * is the point — the bank has not paid a fee for an answer it could work out itself.
     */
    KYC_DOCUMENT_EXPIRED,

    /**
     * The provider reported the document itself as not genuine. Beats the confidence bands: a
     * forgery is a forgery whatever number sits beside it.
     */
    KYC_DOCUMENT_INVALID,

    /**
     * The confidence landed at or below the reject threshold (→ FAILED), or strictly between the
     * two thresholds (→ REVIEW). One code, two outcomes, deliberately: the fact is the same and it
     * is the thresholds that decide what to do about it.
     */
    KYC_LOW_CONFIDENCE,

    /**
     * Nobody answered — the retry budget was spent and the fallback did not answer either.
     *
     * <p>Always REVIEW, <b>never</b> FAILED. Rejected is a business answer about the applicant, and
     * an outage says nothing whatsoever about the applicant.</p>
     */
    KYC_PROVIDER_UNAVAILABLE,

    /**
     * The fallback source is what answered. Rides <i>alongside</i> the outcome code rather than
     * replacing it, because the applicant's result and the route it took there are two different
     * facts and an operator needs both.
     */
    KYC_FAILED_OVER_TO_SECONDARY
}
