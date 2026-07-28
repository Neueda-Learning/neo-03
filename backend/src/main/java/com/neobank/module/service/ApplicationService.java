package com.neobank.module.service;

import com.neobank.module.dto.KycRecordView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.model.Decision;
import com.neobank.module.model.KycRecord;
import com.neobank.module.repository.KycRecordRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>Your module's work happens here. This is the class you came here to write.</h2>
 *
 * <p>An ordinary service class, like the ones you wrote in Week 2 — the difference is only that the
 * layers around it are already built. The controller has answered {@code 202} and let the
 * orchestrator go; {@code integrations.orchestrator} handles both ends of the wire; the repository
 * handles storage. None of that changes when your logic changes.</p>
 *
 * <p>The service extracts the identity fields owned by this module, stores a {@link KycRecord},
 * and reports the outcome to the orchestrator.</p>
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final Executor executor;
    private final KycRecordRepository kycRecords;
    private final OrchestratorClient orchestrator;
    private final Clock clock;
    private final Random random = new Random();

    /**
     * {@code applicationTaskExecutor} is the thread pool Spring Boot configures for you. Tune it in
     * {@code application.yml} under {@code spring.task.execution.*} — pool size matters once your
     * logic calls a slow mock, because that is what limits how many applications you can handle at
     * once.
     */
    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              KycRecordRepository kycRecords,
                              OrchestratorClient orchestrator,
                              Clock clock) {
        this.executor = executor;
        this.kycRecords = kycRecords;
        this.orchestrator = orchestrator;
        this.clock = clock;
    }

    /**
     * Hand the work to the pool and return immediately.
     *
     * <p>The controller calls this and then writes the {@code 202}. <b>Nothing here may block:</b>
     * the orchestrator is holding a connection open, and a module that does its work on the request
     * thread turns a fast journey into a slow one.</p>
     */
    public void processApplicationAsync(ApplicationRequest request) {
        executor.execute(() -> processApplication(request));
    }

    /**
     * Do the work: say something, store something, report something.
     *
     * <p>Package-private on purpose — the outside world goes through
     * {@link #processApplicationAsync}, and a unit test can call this directly on the test thread,
     * which is what makes it testable without a thread pool.</p>
     *
     * <p><b>Deliberately not {@code @Transactional}.</b> The repository's own save is transactional;
     * wrapping the whole method would put the HTTP call inside that transaction, so a slow or
     * unreachable orchestrator could roll back a row this module had already committed. Store
     * first, report second, and let the two fail independently. When you add several writes that
     * must land together, put {@code @Transactional} on a method that does only the writes.</p>
     */
    void processApplication(ApplicationRequest request) {
        String applicationId = request.applicationId();
        try {
            log.info("Processing KYC application — {}", request.summary());
            log.info("Random passport confidence test output — {}", PassportVerification());

            KycAssessment assessment = assess(request);
            kycRecords.save(assessment.record());

            orchestrator.applicationStatusUpdate(
                    applicationId, assessment.decision(), assessment.comment());
        } catch (RuntimeException e) {
            // A module that throws never reports, and the orchestrator then waits out its 30s
            // timeout and ends the journey FAILED with nothing to explain it. So: refer it to a
            // human and say why. Keep this guard when you replace the body above.
            log.error("processApplication failed for {} — referring", applicationId, e);
            orchestrator.applicationStatusUpdate(applicationId, Decision.REFERRED,
                    "module error: " + e);
        }
    }

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<KycRecordView> findAll() {
        return kycRecords.findAllByOrderByCreatedAtDescKycIdDesc().stream()
                .map(KycRecordView::of)
                .toList();
    }

    private KycAssessment assess(ApplicationRequest request) {
        Application application = required(request.application(), "application");
        Application.Applicant applicant = required(application.applicant(), "application.applicant");
        Application.IdentityDocument document =
                required(application.identityDocument(), "application.identityDocument");
        LocalDate expiryDate = LocalDate.parse(required(
                document.expiryDate(), "application.identityDocument.expiryDate"));
        boolean expiresTooSoon = expiryDate.isBefore(LocalDate.now(clock).plusMonths(6));
        String status = expiresTooSoon ? "FAILED" : "VERIFIED";
        Decision decision = expiresTooSoon ? Decision.REJECTED : Decision.ACCEPTED;
        String comment = expiresTooSoon
                ? "identity document expires in less than 6 months"
                : "identity document verified";

        KycRecord record = new KycRecord(
                UUID.randomUUID().toString(),
                request.applicationId(),
                status,
                required(applicant.fullName(), "application.applicant.fullName"),
                required(document.type(), "application.identityDocument.type"),
                required(document.documentId(), "application.identityDocument.documentId"),
                required(document.issuingCountry(),
                        "application.identityDocument.issuingCountry"),
                expiryDate);
        return new KycAssessment(record, decision, comment);
    }

    private PassportVerificationResult PassportVerification() {
        Integer confidence = ThirdPartyPassport();
        if (confidence == null) {
            return new PassportVerificationResult(null, "REVIEW");
        }

        String result = confidence >= 92 ? "ACCEPT" : confidence <= 60 ? "REJECT" : "REVIEW";
        return new PassportVerificationResult(confidence, result);
    }

    private Integer ThirdPartyPassport() {
        boolean networkConnected = random.nextInt(4) < 3;
        if (!networkConnected) {
            return null;
        }

        return switch (random.nextInt(3)) {
            case 0 -> random.nextInt(61);
            case 1 -> 61 + random.nextInt(31);
            default -> 92 + random.nextInt(9);
        };
    }

    private static <T> T required(T value, String field) {
        if (value == null || value instanceof String string && string.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private record PassportVerificationResult(Integer confidence, String result) {
    }

    private record KycAssessment(KycRecord record, Decision decision, String comment) {
    }
}
