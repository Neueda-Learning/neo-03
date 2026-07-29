package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.idprovider.IdVerificationClient;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.KycRecord;
import com.neobank.module.model.ReviewFail;
import com.neobank.module.model.ReviewScore;
import com.neobank.module.model.ThirdPartyAttempt;
import com.neobank.module.repository.KycRecordRepository;
import com.neobank.module.repository.ReviewFailRepository;
import com.neobank.module.repository.ReviewScoreRepository;
import com.neobank.module.repository.ThirdPartyAttemptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The three things the placeholder does, and the guard that keeps a failure reportable.
 *
 * <p>No Spring, no database, no HTTP — the service takes a request and calls two collaborators, so
 * the test is a handful of lines. Keep it that way as you replace the body: logic that needs a
 * running container to test is logic you will stop testing.</p>
 */
class ApplicationServiceTest {

    private KycRecordRepository kycRecords;
    private ThirdPartyAttemptRepository thirdPartyAttempts;
    private ReviewFailRepository reviewFails;
    private ReviewScoreRepository reviewScores;
    private OrchestratorClient orchestrator;
    private IdVerificationClient idVerificationClient;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        kycRecords = mock(KycRecordRepository.class);
        thirdPartyAttempts = mock(ThirdPartyAttemptRepository.class);
        reviewFails = mock(ReviewFailRepository.class);
        reviewScores = mock(ReviewScoreRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        idVerificationClient = mock(IdVerificationClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        service = new ApplicationService(
                Runnable::run,
                kycRecords,
                thirdPartyAttempts,
                reviewFails,
                reviewScores,
                orchestrator,
                idVerificationClient,
                clock);
        when(kycRecords.save(any(KycRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ApplicationRequest request(String id) {
        return request(id, "DRIVING_LICENCE", "2029-08-31");
    }

    private static ApplicationRequest request(String id, String expiryDate) {
        return request(id, "DRIVING_LICENCE", expiryDate);
    }

    private static ApplicationRequest request(String id, String documentType, String expiryDate) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Jonas Meyer", "1979-02-14", null, null, null, null,
                        null, null, null, null, null),
                new Application.IdentityDocument(
                        documentType, "MEYER701794JM9AB", "GB", expiryDate),
                null, null,
                new Application.Product("CREDIT_CARD_STANDARD", 2500),
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void storesTheApplicationAndReportsItAccepted() {
        when(idVerificationClient.verifyDrivingLicense()).thenReturn(96);

        service.processApplication(request("SIM-01"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getKycId()).isNotBlank();
        assertThat(saved.getValue().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getValue().getStatus()).isEqualTo("VERIFIED");
        assertThat(saved.getValue().getName()).isEqualTo("Jonas Meyer");
        assertThat(saved.getValue().getType()).isEqualTo("DRIVING_LICENCE");
        assertThat(saved.getValue().getDocumentId()).isEqualTo("MEYER701794JM9AB");
        assertThat(saved.getValue().getIssuingCountry()).isEqualTo("GB");
        assertThat(saved.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2029, 8, 31));

        ArgumentCaptor<List<ThirdPartyAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(thirdPartyAttempts).saveAll(attempts.capture());
        assertThat(attempts.getValue()).hasSize(1);
        assertThat(attempts.getValue().getFirst().getResult()).isEqualTo("SUCCESS");
        assertThat(attempts.getValue().getFirst().getConfidence()).isEqualTo(96);

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
            "driving_licence verified on attempt 1 (confidence 96)");
    }

    @Test
    void theAsyncEntryPointDoesTheSameWorkThroughTheExecutor() {
        when(idVerificationClient.verifyDrivingLicense()).thenReturn(95);

        service.processApplicationAsync(request("SIM-02"));

        verify(kycRecords).save(any(KycRecord.class));
        verify(orchestrator).applicationStatusUpdate(eq("SIM-02"), eq(Decision.ACCEPTED), any());
    }

    @Test
    void rejectsADocumentThatExpiresInLessThanSixMonths() {
        service.processApplication(request("SIM-04", "2027-01-27"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");

        verify(orchestrator).applicationStatusUpdate(
                "SIM-04",
                Decision.REJECTED,
                "identity document expires in less than 6 months");
        verify(thirdPartyAttempts, never()).saveAll(any());
        verify(idVerificationClient, never()).verifyDrivingLicense();
    }

    @Test
    void acceptsADocumentThatExpiresInExactlySixMonths() {
        when(idVerificationClient.verifyDrivingLicense()).thenReturn(92);

        service.processApplication(request("SIM-05", "2027-01-28"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VERIFIED");

        verify(orchestrator).applicationStatusUpdate(
                "SIM-05", Decision.ACCEPTED, "driving_licence verified on attempt 1 (confidence 92)");
    }

    @Test
    void rejectsWhenThirdPartyConfidenceIsLow() {
        when(idVerificationClient.verifyDrivingLicense()).thenReturn(40);

        service.processApplication(request("SIM-08"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");

        ArgumentCaptor<List<ThirdPartyAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(thirdPartyAttempts).saveAll(attempts.capture());
        assertThat(attempts.getValue()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getResult()).isEqualTo("FAILED");
            assertThat(attempt.getConfidence()).isEqualTo(40);
        });

        verify(orchestrator).applicationStatusUpdate(
                "SIM-08", Decision.REJECTED,
                "driving_licence verification failed on attempt 1 (confidence 40)");
    }

    @Test
    void refersForManualReviewWhenThirdPartyConfidenceIsBorderline() {
        when(idVerificationClient.verifyPassport()).thenReturn(75);

        service.processApplication(request("SIM-09", "PASSPORT", "2029-08-31"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("REVIEW");

        ArgumentCaptor<List<ThirdPartyAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(thirdPartyAttempts).saveAll(attempts.capture());
        assertThat(attempts.getValue()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getDocumentType()).isEqualTo("PASSPORT");
            assertThat(attempt.getResult()).isEqualTo("REVIEW");
            assertThat(attempt.getConfidence()).isEqualTo(75);
        });

        ArgumentCaptor<ReviewScore> review = ArgumentCaptor.forClass(ReviewScore.class);
        verify(reviewScores).save(review.capture());
        assertThat(review.getValue().getKycId()).isEqualTo(saved.getValue().getKycId());
        assertThat(review.getValue().getConfidence()).isEqualTo(75);
        assertThat(review.getValue().getReviewResult()).isEqualTo("REVIEW");
        assertThat(review.getValue().getManualReviewComment()).isNull();
        verify(reviewFails, never()).save(any());

        verify(orchestrator).applicationStatusUpdate(
                "SIM-09", Decision.REFERRED,
                "passport requires manual review on attempt 1 (confidence 75)");
    }

    @Test
    void refersForManualReviewWhenPassportProviderFailsThreeTimes() {
        when(idVerificationClient.verifyPassport()).thenReturn(-1, -1, -1);

        service.processApplication(request("SIM-06", "PASSPORT", "2029-08-31"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("REVIEW");

        ArgumentCaptor<List<ThirdPartyAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(thirdPartyAttempts).saveAll(attempts.capture());
        assertThat(attempts.getValue()).hasSize(3);
        assertThat(attempts.getValue()).allSatisfy(attempt -> {
            assertThat(attempt.getDocumentType()).isEqualTo("PASSPORT");
            assertThat(attempt.getResult()).isEqualTo("UNAVAILABLE");
            assertThat(attempt.getConfidence()).isNull();
        });

        ArgumentCaptor<ReviewFail> review = ArgumentCaptor.forClass(ReviewFail.class);
        verify(reviewFails).save(review.capture());
        assertThat(review.getValue().getKycId()).isEqualTo(saved.getValue().getKycId());
        assertThat(review.getValue().getReviewResult()).isEqualTo("REVIEW");
        assertThat(review.getValue().getManualReviewComment()).isNull();
        verify(reviewScores, never()).save(any());

        verify(orchestrator).applicationStatusUpdate(
                "SIM-06",
                Decision.REFERRED,
                "passport verification unavailable after 3 attempts; manual review required");
    }

    @Test
    void acceptsNationalIdWithoutThirdPartyCall() {
        when(idVerificationClient.verifyNationalId()).thenReturn(95);

        service.processApplication(request("SIM-07", "NATIONAL_ID", "2029-08-31"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VERIFIED");
        ArgumentCaptor<List<ThirdPartyAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(thirdPartyAttempts).saveAll(attempts.capture());
        assertThat(attempts.getValue()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getDocumentType()).isEqualTo("NATIONAL_ID");
            assertThat(attempt.getResult()).isEqualTo("SUCCESS");
            assertThat(attempt.getConfidence()).isEqualTo(95);
        });
        verify(orchestrator).applicationStatusUpdate(
                "SIM-07", Decision.ACCEPTED, "national_id verified on attempt 1 (confidence 95)");
    }

    @Test
    void aFailureIsStillReportedRatherThanLeavingTheJourneyToTimeOut() {
        // The failure mode this guard exists for: a module that throws never reports, and the
        // orchestrator then waits out its 30s timeout and ends the journey FAILED with nothing to
        // explain it. REFERRED with a reason is far more useful than silence.
        doThrow(new IllegalStateException("database on fire"))
                .when(kycRecords).save(any(KycRecord.class));

        service.processApplication(request("SIM-03"));

        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq("SIM-03"), eq(Decision.REFERRED),
                comment.capture());
        assertThat(comment.getValue()).contains("database on fire");
        verifyNoMoreInteractions(orchestrator);
    }

    @Test
    void theBoardShowsWhatWasStored() {
        when(kycRecords.findAllByOrderByCreatedAtDescKycIdDesc())
                .thenReturn(java.util.List.of(new KycRecord(
                        "KYC-1",
                        "SIM-01",
                        "VERIFIED",
                        "Jonas Meyer",
                        "DRIVING_LICENCE",
                        "MEYER701794JM9AB",
                        "GB",
                        LocalDate.of(2029, 8, 31))));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.status()).isEqualTo("VERIFIED");
                    assertThat(view.name()).isEqualTo("Jonas Meyer");
                });
    }
}
