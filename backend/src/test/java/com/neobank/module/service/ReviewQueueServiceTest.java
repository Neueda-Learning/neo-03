package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.dto.ReviewQueueView;
import com.neobank.module.integrations.idprovider.IdVerificationClient;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.KycRecord;
import com.neobank.module.model.ReviewFail;
import com.neobank.module.model.ReviewScore;
import com.neobank.module.repository.KycRecordRepository;
import com.neobank.module.repository.ReviewFailRepository;
import com.neobank.module.repository.ReviewScoreRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewQueueServiceTest {

    private ReviewFailRepository reviewFails;
    private ReviewScoreRepository reviewScores;
    private KycRecordRepository kycRecords;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        reviewFails = mock(ReviewFailRepository.class);
        reviewScores = mock(ReviewScoreRepository.class);
        kycRecords = mock(KycRecordRepository.class);
        service = new ApplicationService(
                Runnable::run,
                kycRecords,
                mock(com.neobank.module.repository.ThirdPartyAttemptRepository.class),
                reviewFails,
                reviewScores,
                mock(OrchestratorClient.class),
                mock(IdVerificationClient.class),
                java.time.Clock.systemUTC());
    }

    @Test
    void combinesBothSourcesOldestFirstAndUsesStableTieBreakers() {
        ReviewScore earlyScore = score("S-1", "KYC-1", "2026-07-20T09:00:00Z", 74);
        ReviewScore sameTimeScore = score("S-2", "KYC-3", "2026-07-21T09:00:00Z", 68);
        ReviewFail sameTimeFail = fail("F-2", "KYC-2", "2026-07-21T09:00:00Z");
        ReviewFail lateFail = fail("F-3", "KYC-4", "2026-07-22T09:00:00Z");
        when(reviewScores.findTop10ByOrderByCreatedAtAscReviewScoreIdAsc())
                .thenReturn(List.of(earlyScore, sameTimeScore));
        when(reviewFails.findTop10ByOrderByCreatedAtAscReviewFailIdAsc())
                .thenReturn(List.of(sameTimeFail, lateFail));
        List<KycRecord> records = List.of(
                record("KYC-1", "APP-1"),
                record("KYC-2", "APP-2"),
                record("KYC-3", "APP-3"),
                record("KYC-4", "APP-4"));
        when(kycRecords.findAllById(any())).thenReturn(records);

        assertThat(service.findEarliestReviewQueue())
                .extracting(ReviewQueueView::applicationId)
                .containsExactly("APP-1", "APP-2", "APP-3", "APP-4");
        assertThat(service.findEarliestReviewQueue())
                .extracting(ReviewQueueView::source)
                .containsExactly("SCORE", "FAIL", "SCORE", "FAIL");
        assertThat(service.findEarliestReviewQueue().get(0).confidence()).isEqualTo(74);
        assertThat(service.findEarliestReviewQueue().get(1).confidence()).isNull();
    }

    @Test
    void capsTheCombinedQueueAtTenRows() {
        List<ReviewFail> fails = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> fail("F-" + index, "KF-" + index,
                        "2026-07-22T%02d:00:00Z".formatted(index)))
                .toList();
        List<ReviewScore> scores = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> score("S-" + index, "KS-" + index,
                        "2026-07-21T%02d:00:00Z".formatted(index), 70))
                .toList();
        when(reviewFails.findTop10ByOrderByCreatedAtAscReviewFailIdAsc()).thenReturn(fails);
        when(reviewScores.findTop10ByOrderByCreatedAtAscReviewScoreIdAsc()).thenReturn(scores);
        List<KycRecord> records = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> record("KS-" + index, "APP-S-" + index))
                .toList();
        when(kycRecords.findAllById(any())).thenReturn(records);

        assertThat(service.findEarliestReviewQueue())
                .hasSize(10)
                .extracting(ReviewQueueView::source)
                .containsOnly("SCORE");
    }

    @Test
    void returnsAnEmptyQueueWithoutLookingUpKycRecords() {
        when(reviewFails.findTop10ByOrderByCreatedAtAscReviewFailIdAsc()).thenReturn(List.of());
        when(reviewScores.findTop10ByOrderByCreatedAtAscReviewScoreIdAsc()).thenReturn(List.of());

        assertThat(service.findEarliestReviewQueue()).isEmpty();
        verifyNoInteractions(kycRecords);
    }

    private static ReviewFail fail(String id, String kycId, String createdAt) {
        ReviewFail review = mock(ReviewFail.class);
        when(review.getReviewFailId()).thenReturn(id);
        when(review.getKycId()).thenReturn(kycId);
        when(review.getCreatedAt()).thenReturn(Instant.parse(createdAt));
        when(review.getReviewResult()).thenReturn("REVIEW");
        when(review.getManualReviewComment()).thenReturn("provider unavailable");
        return review;
    }

    private static ReviewScore score(String id, String kycId, String createdAt, int confidence) {
        ReviewScore review = mock(ReviewScore.class);
        when(review.getReviewScoreId()).thenReturn(id);
        when(review.getKycId()).thenReturn(kycId);
        when(review.getCreatedAt()).thenReturn(Instant.parse(createdAt));
        when(review.getReviewResult()).thenReturn("REVIEW");
        when(review.getConfidence()).thenReturn(confidence);
        when(review.getManualReviewComment()).thenReturn("low confidence");
        return review;
    }

    private static KycRecord record(String kycId, String applicationId) {
        KycRecord record = mock(KycRecord.class);
        when(record.getKycId()).thenReturn(kycId);
        when(record.getApplicationId()).thenReturn(applicationId);
        return record;
    }
}
