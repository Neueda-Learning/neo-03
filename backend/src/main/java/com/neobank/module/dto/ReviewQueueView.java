package com.neobank.module.dto;

import java.time.Instant;

/** One row in the operator's oldest-first manual-review queue. */
public record ReviewQueueView(
        String applicationId,
        String kycId,
        String source,
        Instant createdAt,
        String reviewResult,
        Integer confidence,
        String comment) {
}
