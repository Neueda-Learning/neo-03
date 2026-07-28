package com.neobank.module.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.KycRecord;
import com.neobank.module.repository.KycRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private OrchestratorClient orchestrator;
    private ApplicationService service;

    @BeforeEach
    void setUp() {
        kycRecords = mock(KycRecordRepository.class);
        orchestrator = mock(OrchestratorClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        service = new ApplicationService(Runnable::run, kycRecords, orchestrator, clock);
        when(kycRecords.save(any(KycRecord.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static ApplicationRequest request(String id) {
        return request(id, "2029-08-31");
    }

    private static ApplicationRequest request(String id, String expiryDate) {
        Application application = new Application(
                id, "MOBILE_APP", "2026-07-25T09:14:00Z",
                new Application.Applicant("Jonas Meyer", "1979-02-14", null, null, null, null,
                        null, null, null, null, null),
                new Application.IdentityDocument(
                        "DRIVING_LICENCE", "MEYER701794JM9AB", "GB", expiryDate),
                null, null,
                new Application.Product("CREDIT_CARD_STANDARD", 2500),
                null, null);
        return new ApplicationRequest(id, "corr-1", "process-application", application);
    }

    @Test
    void storesTheApplicationAndReportsItAccepted() {
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

        verify(orchestrator).applicationStatusUpdate("SIM-01", Decision.ACCEPTED,
                "identity document verified");
    }

    @Test
    void theAsyncEntryPointDoesTheSameWorkThroughTheExecutor() {
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
    }

    @Test
    void acceptsADocumentThatExpiresInExactlySixMonths() {
        service.processApplication(request("SIM-05", "2027-01-28"));

        ArgumentCaptor<KycRecord> saved = ArgumentCaptor.forClass(KycRecord.class);
        verify(kycRecords).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("VERIFIED");

        verify(orchestrator).applicationStatusUpdate(
                "SIM-05", Decision.ACCEPTED, "identity document verified");
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
