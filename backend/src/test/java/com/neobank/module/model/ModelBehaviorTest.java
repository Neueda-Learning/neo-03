package com.neobank.module.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModelBehaviorTest {

    @Test
    void demoShowcaseStoresTheDecisionNameAndCreatedTimestamp() {
        DemoShowcase showcase = new DemoShowcase("APP-1", Decision.ACCEPTED);

        showcase.onCreate();

        assertThat(showcase.getApplicationId()).isEqualTo("APP-1");
        assertThat(showcase.getStatus()).isEqualTo("ACCEPTED");
        assertThat(showcase.getCreatedAt()).isNotNull();
    }

    @Test
    void thirdPartyAttemptExposesItsFieldsAndSetsCreatedAt() {
        ThirdPartyAttempt attempt = new ThirdPartyAttempt(
                "attempt-1", "kyc-1", 2, "PASSPORT", "MATCHED", 88, "clear");

        attempt.onCreate();

        assertThat(attempt.getThirdPartyAttemptId()).isEqualTo("attempt-1");
        assertThat(attempt.getKycId()).isEqualTo("kyc-1");
        assertThat(attempt.getAttemptNumber()).isEqualTo(2);
        assertThat(attempt.getDocumentType()).isEqualTo("PASSPORT");
        assertThat(attempt.getResult()).isEqualTo("MATCHED");
        assertThat(attempt.getConfidence()).isEqualTo(88);
        assertThat(attempt.getComment()).isEqualTo("clear");
        assertThat(attempt.getCreatedAt()).isNotNull();
    }

    @Test
    void reviewFailRecordsManualDecisions() {
        Instant decidedAt = Instant.parse("2026-07-29T08:00:00Z");
        ReviewFail reviewFail = new ReviewFail("fail-1", "kyc-1", null, null, null);

        reviewFail.onCreate();
        reviewFail.recordManualDecision("MANUALLY_REJECTED", "mismatch", decidedAt);

        assertThat(reviewFail.getReviewFailId()).isEqualTo("fail-1");
        assertThat(reviewFail.getKycId()).isEqualTo("kyc-1");
        assertThat(reviewFail.getCreatedAt()).isNotNull();
        assertThat(reviewFail.getUpdatedAt()).isEqualTo(decidedAt);
        assertThat(reviewFail.getReviewResult()).isEqualTo("MANUALLY_REJECTED");
        assertThat(reviewFail.getManualReviewComment()).isEqualTo("mismatch");
    }

    @Test
    void reviewScoreRecordsManualDecisions() {
        Instant decidedAt = Instant.parse("2026-07-29T08:05:00Z");
        ReviewScore reviewScore = new ReviewScore("score-1", "kyc-2", null, 67, null, null);

        reviewScore.onCreate();
        reviewScore.recordManualDecision("MANUALLY_APPROVED", "verified by analyst", decidedAt);

        assertThat(reviewScore.getReviewScoreId()).isEqualTo("score-1");
        assertThat(reviewScore.getKycId()).isEqualTo("kyc-2");
        assertThat(reviewScore.getConfidence()).isEqualTo(67);
        assertThat(reviewScore.getCreatedAt()).isNotNull();
        assertThat(reviewScore.getUpdatedAt()).isEqualTo(decidedAt);
        assertThat(reviewScore.getReviewResult()).isEqualTo("MANUALLY_APPROVED");
        assertThat(reviewScore.getManualReviewComment()).isEqualTo("verified by analyst");
    }
}
