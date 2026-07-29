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

import com.neobank.module.integrations.idprovider.Agency;
import com.neobank.module.integrations.idprovider.AttemptResult;
import com.neobank.module.integrations.idprovider.ProviderAnswer;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The decision, and nothing else.
 *
 * <p>The provider gateway is mocked, so these tests are about one question only: given what the
 * identity source said, what does this module answer and how does it explain itself? How many times
 * it called, how long it waited and when it gave up are {@link ProviderGatewayTest}'s business.</p>
 *
 * <p>No Spring, no database, no HTTP. Logic that needs a running container to test is logic that
 * stops being tested.</p>
 */
class ApplicationServiceTest {

    private KycRecordRepository kycRecords;
    private ThirdPartyAttemptRepository thirdPartyAttempts;
    private ReviewFailRepository reviewFails;
    private ReviewScoreRepository reviewScores;
    private OrchestratorClient orchestrator;
    private ProviderGateway gateway;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        kycRecords = mock(KycRecordRepository.class);
        thirdPartyAttempts = mock(ThirdPartyAttemptRepository.class);
        reviewFails = mock(ReviewFailRepository.class);
        reviewScores = mock(ReviewScoreRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        gateway = mock(ProviderGateway.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        service = new ApplicationService(
                Runnable::run,
                kycRecords,
                thirdPartyAttempts,
                reviewFails,
                reviewScores,
                orchestrator,
                gateway,
                clock,
                92,
                60);
        when(kycRecords.save(any(KycRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- fixtures ----------------------------------------------------------------------

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

    private ThirdPartyAttempt attemptRow(AttemptResult result, Integer confidence) {
        return new ThirdPartyAttempt("att-1", "kyc-1", 1, "DRIVING_LICENCE", result.name(),
                confidence, "…", Agency.NATIONAL.name(), Instant.parse("2026-07-28T00:00:00Z"),
                12, "nat-abc");
    }

    /** The provider answered with this confidence, first time, on the primary. */
    private void providerAnswers(int confidence) {
        providerAnswers(confidence, true, Agency.NATIONAL, false);
    }

    private void providerAnswers(int confidence, boolean genuine, Agency agency, boolean failedOver) {
        ProviderAnswer answer = new ProviderAnswer("nat-abc", agency.name(), confidence,
                List.of(new ProviderAnswer.Check("documentGenuine", genuine),
                        new ProviderAnswer.Check("nameMatched", true)));
        when(gateway.verify(any(), any())).thenReturn(new ProviderGateway.ProviderOutcome(
                answer, agency, failedOver,
                List.of(attemptRow(AttemptResult.ANSWERED, confidence)), null));
    }

    /** Nobody answered — the ladder and the fallback were both spent. */
    private void providerNeverAnswers() {
        when(gateway.verify(any(), any())).thenReturn(new ProviderGateway.ProviderOutcome(
                null, null, false,
                List.of(attemptRow(AttemptResult.TIMEOUT, null),
                        attemptRow(AttemptResult.TIMEOUT, null),
                        attemptRow(AttemptResult.TIMEOUT, null),
                        attemptRow(AttemptResult.REFUSED, null)),
                AttemptResult.REFUSED));
    }

    private String reportedComment(String applicationId, Decision decision) {
        ArgumentCaptor<String> comment = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).applicationStatusUpdate(eq(applicationId), eq(decision), comment.capture());
        return comment.getValue();
    }

    // ---- the bands ---------------------------------------------------------------------

    @Test
    @DisplayName("A high confidence verifies, is stored, and is reported with KYC_VERIFIED")
    void storesTheApplicationAndReportsItAccepted() {
        providerAnswers(96);

        service.processApplication(request("SIM-01"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getKycId()).isNotBlank();
        assertThat(saved.getValue().getApplicationId()).isEqualTo("SIM-01");
        assertThat(saved.getValue().getStatus()).isEqualTo("VERIFIED");
        assertThat(saved.getValue().getName()).isEqualTo("Jonas Meyer");
        assertThat(saved.getValue().getType()).isEqualTo("DRIVING_LICENCE");
        assertThat(saved.getValue().getIssuingCountry()).isEqualTo("GB");
        assertThat(saved.getValue().getExpiryDate()).isEqualTo(LocalDate.of(2029, 8, 31));

        verify(thirdPartyAttempts).saveAll(any());
        assertThat(reportedComment("SIM-01", Decision.ACCEPTED))
                .startsWith("KYC_VERIFIED")
                .contains("96");
    }

    @Test
    @DisplayName("Exactly the accept threshold PASSES — the >= boundary, not >")
    void confidenceExactlyAtTheAcceptThresholdVerifies() {
        // Maria Nowak's checkpoint. The whole reason the mock pins her at 92 rather than 93: a
        // module that reads this boundary as ">" parks a customer it should have approved, and
        // every other test in this class would still be green.
        providerAnswers(92);

        service.processApplication(request("SIM-12"));

        verify(kycRecords).save(any(KycRecord.class));
        assertThat(reportedComment("SIM-12", Decision.ACCEPTED)).startsWith("KYC_VERIFIED");
    }

    @Test
    @DisplayName("One below the accept threshold parks for review")
    void confidenceOneBelowAcceptGoesToReview() {
        providerAnswers(91);

        service.processApplication(request("SIM-13"));

        assertThat(reportedComment("SIM-13", Decision.REFERRED)).startsWith("KYC_LOW_CONFIDENCE");
    }

    @Test
    @DisplayName("Exactly the reject threshold FAILS — the <= boundary, mirroring accept")
    void confidenceExactlyAtTheRejectThresholdFails() {
        providerAnswers(60);

        service.processApplication(request("SIM-14"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(reportedComment("SIM-14", Decision.REJECTED)).startsWith("KYC_LOW_CONFIDENCE");
    }

    @Test
    @DisplayName("One above the reject threshold parks rather than failing")
    void confidenceOneAboveRejectGoesToReview() {
        providerAnswers(61);

        service.processApplication(request("SIM-15"));

        assertThat(reportedComment("SIM-15", Decision.REFERRED)).startsWith("KYC_LOW_CONFIDENCE");
    }

    @Test
    @DisplayName("A borderline score parks the case AND queues it with the score attached")
    void refersForManualReviewWhenConfidenceIsBorderline() {
        providerAnswers(74);

        service.processApplication(request("SIM-09", "PASSPORT", "2029-08-31"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("REVIEW");

        ArgumentCaptor<ReviewScore> review = ArgumentCaptor.forClass(ReviewScore.class);
        verify(reviewScores).save(review.capture());
        assertThat(review.getValue().getKycId()).isEqualTo(saved.getValue().getKycId());
        assertThat(review.getValue().getConfidence()).isEqualTo(74);
        assertThat(review.getValue().getReviewResult()).isEqualTo("REVIEW");
        assertThat(review.getValue().getManualReviewComment()).isNull();
        // A low score is a scored review, not a failed-provider review — different queue, and
        // the analyst needs to know which one they are looking at.
        verify(reviewFails, never()).save(any());

        assertThat(reportedComment("SIM-09", Decision.REFERRED))
                .startsWith("KYC_LOW_CONFIDENCE")
                .contains("74");
    }

    // ---- the gates that beat the bands ---------------------------------------------------

    @Test
    @DisplayName("A document reported not genuine FAILS even with a passing confidence")
    void aForgedDocumentFailsWhateverTheConfidence() {
        // 99 is comfortably above the accept threshold. A convincing forgery scores well — that is
        // what makes it convincing — so the forgery check has to come first or it never fires.
        providerAnswers(99, false, Agency.NATIONAL, false);

        service.processApplication(request("SIM-16"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(reportedComment("SIM-16", Decision.REJECTED)).startsWith("KYC_DOCUMENT_INVALID");
    }

    @Test
    @DisplayName("An expired document fails WITHOUT the provider ever being called")
    void rejectsADocumentThatExpiresInLessThanSixMonths() {
        service.processApplication(request("SIM-04", "2027-01-27"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");

        assertThat(reportedComment("SIM-04", Decision.REJECTED)).startsWith("KYC_DOCUMENT_EXPIRED");
        // Zero attempt rows and zero gateway calls. This is the assertion that proves no provider
        // fee was paid for an answer the date alone gave us.
        verify(thirdPartyAttempts, never()).saveAll(any());
        verify(gateway, never()).verify(any(), any());
    }

    @Test
    @DisplayName("Exactly six months to expiry still goes to the provider")
    void acceptsADocumentThatExpiresInExactlySixMonths() {
        providerAnswers(92);

        service.processApplication(request("SIM-05", "2027-01-28"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VERIFIED");
        verify(gateway).verify(any(), any());
    }

    // ---- outage and failover ------------------------------------------------------------

    @Test
    @DisplayName("An outage PARKS the case — never rejects it, and never says the applicant failed")
    void refersWhenNoIdentitySourceAnswers() {
        providerNeverAnswers();

        service.processApplication(request("SIM-06", "PASSPORT", "2029-08-31"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("REVIEW");

        ArgumentCaptor<List<ThirdPartyAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(thirdPartyAttempts).saveAll(attempts.capture());
        assertThat(attempts.getValue()).hasSize(4);

        // A failed-provider review, not a scored one: there is no score to review.
        ArgumentCaptor<ReviewFail> review = ArgumentCaptor.forClass(ReviewFail.class);
        verify(reviewFails).save(review.capture());
        assertThat(review.getValue().getKycId()).isEqualTo(saved.getValue().getKycId());
        assertThat(review.getValue().getReviewResult()).isEqualTo("REVIEW");
        verify(reviewScores, never()).save(any());

        assertThat(reportedComment("SIM-06", Decision.REFERRED))
                .startsWith("KYC_PROVIDER_UNAVAILABLE")
                .contains("4 attempts");
    }

    @Test
    @DisplayName("A failover is reported alongside the outcome, not instead of it")
    void failoverAppendsItsOwnReasonCode() {
        providerAnswers(95, true, Agency.TAX, true);

        service.processApplication(request("SIM-17"));

        // Both facts survive: the applicant verified, AND the verdict came from a source that
        // never saw the document. An operator reviewing this case needs to know both.
        assertThat(reportedComment("SIM-17", Decision.ACCEPTED))
                .startsWith("KYC_VERIFIED")
                .endsWith("KYC_FAILED_OVER_TO_SECONDARY");
    }

    @Test
    @DisplayName("The fallback cannot answer documentGenuine, and that is not a forgery")
    void aMissingDocumentCheckIsNotTreatedAsAFailedOne() {
        // The tax agency reports three checks and never mentions documentGenuine. Reading a
        // missing check as a failed one would make EVERY failover reject the applicant — the
        // exact opposite of what a fallback is for.
        ProviderAnswer taxAnswer = new ProviderAnswer("tax-abc", "TAX_AGENCY", 95,
                List.of(new ProviderAnswer.Check("nameMatched", true),
                        new ProviderAnswer.Check("dobConsistent", true)));
        when(gateway.verify(any(), any())).thenReturn(new ProviderGateway.ProviderOutcome(
                taxAnswer, Agency.TAX, true,
                List.of(attemptRow(AttemptResult.ANSWERED, 95)), null));

        service.processApplication(request("SIM-18"));

        assertThat(reportedComment("SIM-18", Decision.ACCEPTED)).startsWith("KYC_VERIFIED");
    }

    // ---- plumbing -------------------------------------------------------------------------

    @Test
    @DisplayName("The async entry point does the same work through the executor")
    void theAsyncEntryPointDoesTheSameWorkThroughTheExecutor() {
        providerAnswers(95);

        service.processApplicationAsync(request("SIM-02"));

        verify(kycRecords).save(any(KycRecord.class));
        verify(orchestrator).applicationStatusUpdate(eq("SIM-02"), eq(Decision.ACCEPTED), any());
    }

    @Test
    @DisplayName("A crash is still reported rather than leaving the journey to time out")
    void aFailureIsStillReportedRatherThanLeavingTheJourneyToTimeOut() {
        // The failure mode this guard exists for: a module that throws never reports, and the
        // orchestrator then waits out its 30s timeout and ends the journey FAILED with nothing to
        // explain it. REFERRED with a reason is far more useful than silence.
        providerAnswers(95);
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
    @DisplayName("Inverted thresholds fail at STARTUP, not silently at decision time")
    void invertedThresholdsAreRejectedOnConstruction() {
        // A reject threshold above accept does not throw when a decision is made — it quietly
        // empties the REVIEW band, so every borderline case passes or fails and nothing ever
        // reaches a human. That is a compliance failure that looks like a working system.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ApplicationService(
                        Runnable::run, kycRecords, thirdPartyAttempts, reviewFails, reviewScores,
                        orchestrator, gateway, Clock.systemUTC(), 60, 92))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be below");
    }

    @Test
    @DisplayName("The board shows what was stored")
    void theBoardShowsWhatWasStored() {
        when(kycRecords.findAllByOrderByCreatedAtDescKycIdDesc())
                .thenReturn(List.of(new KycRecord(
                        "KYC-1", "SIM-01", "VERIFIED", "Jonas Meyer", "DRIVING_LICENCE",
                        "MEYER701794JM9AB", "GB", LocalDate.of(2029, 8, 31))));

        assertThat(service.findAll())
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.applicationId()).isEqualTo("SIM-01");
                    assertThat(view.status()).isEqualTo("VERIFIED");
                    assertThat(view.name()).isEqualTo("Jonas Meyer");
                });
    }
}
