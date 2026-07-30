package com.neobank.module.integrations.idprovider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * What an identity source answered.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} on purpose: the provider is allowed to add
 * a field — the module brief's own upgrade path adds a {@code liveness} block — without breaking
 * every module that reads it. Unknown fields in, nothing extra out.</p>
 *
 * @param providerRef the source's reference for this check, prefixed by agency ({@code nat-…})
 * @param confidence  0-100. Boxed, because a response missing it is a different fact from zero,
 *                    and zero would silently reject an applicant
 * @param checks      what the source actually looked at
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderAnswer(
        String providerRef,
        String agency,
        Integer confidence,
        List<Check> checks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Check(String name, Boolean passed) {
    }

    /**
     * Whether the source reported the document itself as genuine.
     *
     * <p><b>Absent means "not asserted", and that is not the same as "forged".</b> Only the
     * National Identity Agency holds the document registers; the Tax Agency answers three checks
     * and never mentions {@code documentGenuine}. Reading a missing check as a failure would make
     * every failover reject the applicant — the exact opposite of what a fallback is for.</p>
     */
    public boolean documentReportedForged() {
        if (checks == null) {
            return false;
        }
        return checks.stream()
                .filter(check -> "documentGenuine".equals(check.name()))
                .anyMatch(check -> Boolean.FALSE.equals(check.passed()));
    }
}
