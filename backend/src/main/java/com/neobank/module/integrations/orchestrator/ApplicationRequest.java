package com.neobank.module.integrations.orchestrator;

import jakarta.validation.constraints.NotBlank;

/**
 * What the orchestrator POSTs to {@code /api/v1/applications} — the envelope, and the
 * {@link Application} inside it.
 *
 * <p><b>Fixed by the system.</b> See {@code package-info.java}.</p>
 *
 * @param applicationId the id everything keys on. The only validated field: an envelope without
 *                      one is rejected {@code 400} before your service is called, because a
 *                      decision you cannot report is worse than no decision.
 * @param correlationId ties this call to one customer journey across all ten modules; log it
 * @param command       what you are being asked to do, e.g. {@code process-application}. Read it,
 *                      do not switch on it — the orchestrator currently sends one value to
 *                      everybody
 * @param application   the whole application, every field, typed
 */
public record ApplicationRequest(
        @NotBlank String applicationId,
        String correlationId,
        String command,
        Application application) {

    /**
     * The one-line summary every module logs on receipt, e.g.
     * {@code SIM-01 corr=sim-0001 applicant='Maria Nowak' product=CREDIT_CARD_REWARDS limit=3000
     * channel=MOBILE_APP}.
     *
     * <p>Everything is null-guarded and missing values read as {@code ?}. A malformed application
     * must still produce a log line — an unlogged request is one you cannot investigate.</p>
     */
    public String summary() {
        Application.Applicant applicant = application == null ? null : application.applicant();
        Application.Product product = application == null ? null : application.product();
        return "%s corr=%s applicant='%s' product=%s limit=%s channel=%s".formatted(
                applicationId,
                shorten(correlationId),
                applicant == null ? "?" : str(applicant.fullName()),
                product == null ? "?" : str(product.productCode()),
                product == null ? "?" : str(product.requestedCreditLimit()),
                application == null ? "?" : str(application.channel()));
    }

    private static String str(Object value) {
        return value == null ? "?" : String.valueOf(value);
    }

    private static String shorten(String correlationId) {
        if (correlationId == null) {
            return "?";
        }
        return correlationId.length() > 8 ? correlationId.substring(0, 8) : correlationId;
    }
}
