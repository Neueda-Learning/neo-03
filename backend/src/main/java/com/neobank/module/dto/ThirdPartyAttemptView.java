package com.neobank.module.dto;

import com.neobank.module.model.ThirdPartyAttempt;
import java.time.Instant;

/**
 * One call to one identity agency, as the operator screen sees it.
 *
 * <p>A case's attempts are its evidence: three rows against the primary and one against the
 * fallback say "the provider was down", one row saying {@code ANSWERED} says "the applicant was
 * doubtful", and zero rows say "we never needed to ask". Those are three different conversations
 * with three different people, and until this record existed the board showed the same badge for
 * all of them.</p>
 *
 * <p>Three fields of the entity are deliberately NOT here:</p>
 * <ul>
 *   <li>{@code thirdPartyAttemptId} — a surrogate key the UI has no use for. Leaking one is the
 *       habit {@code DemoShowcaseView} was written to avoid.</li>
 *   <li>{@code kycId} — it is the path this was fetched by, so repeating it in every element is
 *       noise.</li>
 *   <li>{@code createdAt} — identical across every row of a case, because they are saved in one
 *       batch. Showing it beside {@code requestedAt} would invite someone to read the wrong one.</li>
 * </ul>
 *
 * <p>{@code attemptNumber} is unique within a case, so it is the list's natural key.</p>
 */
public record ThirdPartyAttemptView(

        /** Position on the ladder, counted across BOTH agencies: 1, 2, 3 primary then 4 fallback. */
        Integer attemptNumber,

        /** {@code NATIONAL} or {@code TAX}. Null on rows written before the module had a provider. */
        String agency,

        /** {@code ANSWERED} · {@code TIMEOUT} · {@code ERROR} · {@code REFUSED} · {@code SHORT_CIRCUITED}. */
        String result,

        /** The 0-100 score, when this attempt got one. Null on every non-{@code ANSWERED} row. */
        Integer confidence,

        /**
         * Round-trip time. A timeout lands at roughly the timeout budget, NOT at however slow the
         * provider actually was — which is how you tell a working read timeout from a broken one.
         */
        Integer latencyMs,

        /**
         * When the call went out.
         *
         * <p>The gaps between consecutive values are the retry ladder's backoff — 1s, then 2s —
         * and are the one thing about it you cannot verify by reading the code.</p>
         */
        Instant requestedAt,

        /** The agency's own reference, when it answered. */
        String providerRef,

        /** What happened, in words. Never contains a document number. */
        String comment) {

    public static ThirdPartyAttemptView of(ThirdPartyAttempt row) {
        return new ThirdPartyAttemptView(
                row.getAttemptNumber(),
                row.getAgency(),
                row.getResult(),
                row.getConfidence(),
                row.getLatencyMs(),
                row.getRequestedAt(),
                row.getProviderRef(),
                row.getComment());
    }
}
