package com.neobank.module.integrations.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The {@code RECEIVED} log line is part of the contract — every module writes exactly one per
 * application — so it is pinned like any other wire format. It must survive a half-empty
 * application, because the malformed ones are precisely the ones you will need the log for.
 */
class ApplicationRequestTest {

    private static Application application(String fullName, String productCode, Integer limit) {
        return new Application(
                "APP-9", "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant(fullName, null, null, null, null, null,
                        null, null, null, null, null),
                null, null, null,
                new Application.Product(productCode, limit),
                null, null);
    }

    @Test
    void summaryRendersTheApplicationDetails() {
        String line = new ApplicationRequest("APP-9", "8f14e45f-ea6d-4b1c", "process-application",
                application("Maria Nowak", "CREDIT_CARD_REWARDS", 3000)).summary();

        assertThat(line).isEqualTo(
                "APP-9 corr=8f14e45f applicant='Maria Nowak' "
                        + "product=CREDIT_CARD_REWARDS limit=3000 channel=MOBILE_APP");
    }

    @Test
    void summarySurvivesAnAbsentApplication() {
        // SIM-26's territory: an envelope so broken there is nothing inside it. It still logs.
        String line = new ApplicationRequest("APP-9", null, "process-application", null).summary();

        assertThat(line).isEqualTo("APP-9 corr=? applicant='?' product=? limit=? channel=?");
    }

    @Test
    void summarySurvivesEmptyNestedObjects() {
        // The likelier shape: an application that arrived with most groups missing. Every nested
        // record is null here, and none of it may throw on the logging path.
        String line = new ApplicationRequest("APP-9", "short", "process-application",
                new Application(null, null, null, null, null, null, null, null, null, null))
                .summary();

        assertThat(line).isEqualTo("APP-9 corr=short applicant='?' product=? limit=? channel=?");
    }

    @Test
    void aMissingFieldInsideAPresentObjectStillReadsAsQuestionMark() {
        String line = new ApplicationRequest("APP-9", "8f14e45f-ea6d-4b1c", "process-application",
                application(null, null, null)).summary();

        assertThat(line).isEqualTo(
                "APP-9 corr=8f14e45f applicant='?' product=? limit=? channel=MOBILE_APP");
    }
}
