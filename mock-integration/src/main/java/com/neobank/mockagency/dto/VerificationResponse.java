package com.neobank.mockagency.dto;

import com.neobank.mockagency.model.Agency;
import java.time.Instant;
import java.util.List;

/**
 * What an identity source answers.
 *
 * <p>One score and a list of per-check results, not a verdict: the source reports what it found and
 * the bank decides what to do about it. That split is deliberate — the thresholds are the bank's
 * compliance policy, and moving one must not require the provider to be redeployed.</p>
 *
 * @param providerRef  the source's own reference for this check, prefixed by agency
 *                     ({@code nat-…} / {@code tax-…}) so a reference names where it came from
 * @param agency       which source answered
 * @param confidence   0-100
 * @param checks       what was actually checked. The National Identity Agency reports four; the Tax
 *                     Agency reports three — it has never seen the document, so it cannot speak to
 *                     {@code documentGenuine}
 * @param checkedAt    when this answer was produced
 */
public record VerificationResponse(
        String providerRef,
        Agency agency,
        int confidence,
        List<Check> checks,
        Instant checkedAt) {

    /** One thing the source looked at, and whether it was satisfied. */
    public record Check(String name, boolean passed) {
    }
}
