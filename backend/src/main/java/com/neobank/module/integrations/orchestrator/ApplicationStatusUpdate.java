package com.neobank.module.integrations.orchestrator;

/**
 * What this module PUTs to the orchestrator once it has an answer:
 * {@code PUT {orchestratorUrl}/api/v1/applications/{applicationId}}.
 *
 * <p><b>Three fields, and no {@code applicationId} — it is in the URL.</b> This is an update to an
 * application the orchestrator already knows about, so the id identifies the resource rather than
 * riding in the body. Sending it twice would only create a way for the two to disagree.</p>
 *
 * <p>Everything your module must report fits in {@code status} and {@code comment}. Anything
 * richer — the checks you ran, the values you saw — stays in your own database and is served from
 * your own {@code GET} endpoints; the orchestrator does not want it and will not store it.</p>
 *
 * <p><b>Fixed by the system.</b> See {@code package-info.java}.</p>
 *
 * @param serviceId which module is reporting — the orchestrator matches this against the step it
 *                  is waiting on, so it must be your assigned id
 * @param status    {@code ACCEPTED} · {@code REJECTED} · {@code REFERRED}. Only the first advances
 *                  the journey.
 * @param comment   why, in words a bank employee could read to a customer
 */
public record ApplicationStatusUpdate(
        String serviceId,
        String status,
        String comment) {
}
