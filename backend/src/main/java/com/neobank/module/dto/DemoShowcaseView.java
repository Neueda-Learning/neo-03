package com.neobank.module.dto;

import com.neobank.module.model.DemoShowcase;
import java.time.Instant;

/**
 * What {@code GET /api/v1/applications} returns — this module's own API, not the orchestrator's.
 *
 * <p>Why a record at all, for three columns? Because the alternative is returning the JPA entity,
 * and that is a habit that costs you later: the surrogate {@code id} leaks, and the first time you
 * add a lazy association to your real table it is serialised on the wire (or throws). A view
 * record is the boundary between "how it is stored" and "what the UI sees", and unlike the records
 * in {@code integrations.orchestrator} it is yours — add whatever your operator screen needs.</p>
 *
 * <p>⚠️ Placeholder shape, like the entity behind it. See {@link DemoShowcase}.</p>
 */
public record DemoShowcaseView(
        String applicationId,
        String status,
        Instant createdAt) {

    public static DemoShowcaseView of(DemoShowcase row) {
        return new DemoShowcaseView(row.getApplicationId(), row.getStatus(), row.getCreatedAt());
    }
}
