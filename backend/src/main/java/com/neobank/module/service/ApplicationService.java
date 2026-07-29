package com.neobank.module.service;

import com.neobank.module.dto.KycRecordView;
import com.neobank.module.dto.ManualReviewDecisionRequest;
import com.neobank.module.dto.ReviewQueueView;
import com.neobank.module.integrations.orchestrator.Application;
import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.integrations.orchestrator.OrchestratorClient;
import com.neobank.module.integrations.idprovider.IdVerificationClient;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private static final int MAX_THIRD_PARTY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 100L;
    private static final int ACCEPT_CONFIDENCE_MIN = 92;
    private static final int REVIEW_CONFIDENCE_MIN = 61;
    private static final int REVIEW_QUEUE_LIMIT = 10;
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private final Executor executor;
    private final KycRecordRepository kycRecords;
    private final ThirdPartyAttemptRepository thirdPartyAttempts;
    private final ReviewFailRepository reviewFails;
    private final ReviewScoreRepository reviewScores;
    private final OrchestratorClient orchestrator;
    private final IdVerificationClient idVerificationClient;
    private final Clock clock;

    /**
     * {@code applicationTaskExecutor} is the thread pool Spring Boot configures for you. Tune it in
     * {@code application.yml} under {@code spring.task.execution.*} — pool size matters once your
     * logic calls a slow mock, because that is what limits how many applications you can handle at
     * once.
     */
    public ApplicationService(@Qualifier("applicationTaskExecutor") Executor executor,
                              KycRecordRepository kycRecords,
                              ThirdPartyAttemptRepository thirdPartyAttempts,
                              ReviewFailRepository reviewFails,
                              ReviewScoreRepository reviewScores,
                              OrchestratorClient orchestrator,
                              IdVerificationClient idVerificationClient,
                              Clock clock) {
        this.executor = executor;
        this.kycRecords = kycRecords;
        this.thirdPartyAttempts = thirdPartyAttempts;
        this.reviewFails = reviewFails;
        this.reviewScores = reviewScores;
        this.orchestrator = orchestrator;
        this.idVerificationClient = idVerificationClient;
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

            KycRecord existingRecord = kycRecords
                    .findFirstByApplicationIdOrderByCreatedAtDescKycIdDesc(applicationId)
                    .orElse(null);
            if (existingRecord != null) {
                reportExistingResult(existingRecord);
                return;
            }

            KycAssessment assessment = assess(request);
            kycRecords.save(assessment.record());
            if (!assessment.attempts().isEmpty()) {
                thirdPartyAttempts.saveAll(assessment.attempts());
            }
            if (assessment.reviewFail() != null) {
                reviewFails.save(assessment.reviewFail());
            }
            if (assessment.reviewScore() != null) {
                reviewScores.save(assessment.reviewScore());
            }

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

    /**
     * Makes receiving the same application idempotent. Older environments may already contain
     * duplicate application ids, so the repository deliberately selects the newest record.
     */
    private void reportExistingResult(KycRecord record) {
        Decision decision = switch (record.getStatus()) {
            case "VERIFIED" -> Decision.ACCEPTED;
            case "FAILED" -> Decision.REJECTED;
            case "REVIEW" -> Decision.REFERRED;
            default -> throw new IllegalStateException(
                    "unknown stored KYC status for " + record.getApplicationId()
                            + ": " + record.getStatus());
        };
        log.info("Application {} already processed as {}; reusing stored result",
                record.getApplicationId(), decision);
        orchestrator.applicationStatusUpdate(
                record.getApplicationId(),
                decision,
                "application already processed; returning stored KYC result: "
                        + record.getStatus());
    }

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<KycRecordView> findAll() {
        return kycRecords.findAllByOrderByCreatedAtDescKycIdDesc().stream()
                .map(KycRecordView::of)
                .toList();
    }

    /** The earliest ten records across both review tables, not ten from each. */
    @Transactional(readOnly = true)
    public List<ReviewQueueView> findEarliestReviewQueue() {
        List<QueueCandidate> candidates = Stream.concat(
                        reviewFails.findTop10ByReviewResultOrderByCreatedAtAscReviewFailIdAsc("REVIEW").stream()
                                .map(QueueCandidate::from),
                        reviewScores.findTop10ByReviewResultOrderByCreatedAtAscReviewScoreIdAsc("REVIEW").stream()
                                .map(QueueCandidate::from))
                .sorted(Comparator.comparing(QueueCandidate::createdAt)
                        .thenComparing(QueueCandidate::source)
                        .thenComparing(QueueCandidate::reviewId))
                .limit(REVIEW_QUEUE_LIMIT)
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<String> kycIds = candidates.stream()
                .map(QueueCandidate::kycId)
                .collect(Collectors.toSet());
        Map<String, KycRecord> recordsByKycId = kycRecords.findAllById(kycIds).stream()
                .collect(Collectors.toMap(KycRecord::getKycId, Function.identity()));

        return candidates.stream()
                .map(candidate -> toReviewQueueView(candidate, recordsByKycId))
                .toList();
    }

    /**
     * Stores the analyst's explanation on the review row that produced the queue entry, records
     * the final KYC status, and reports that final outcome to the orchestrator.
     */
    @Transactional
    public void recordManualReviewDecision(String kycId, ManualReviewDecisionRequest request) {
        KycRecord record = kycRecords.findById(kycId)
                .orElseThrow(() -> new NoSuchElementException("KYC record not found: " + kycId));
        if (!"REVIEW".equals(record.getStatus())) {
            throw new IllegalStateException("KYC record is not awaiting review: " + kycId);
        }

        Decision decision = Decision.valueOf(request.decision());
        String comment = request.comment().trim();
        Instant decidedAt = Instant.now(clock);
        switch (request.source()) {
            case "FAIL" -> reviewFails.findFirstByKycIdAndReviewResult(kycId, "REVIEW")
                    .orElseThrow(() -> new NoSuchElementException("Pending failed-provider review not found: " + kycId))
                    .recordManualDecision(decision.name(), comment, decidedAt);
            case "SCORE" -> reviewScores.findFirstByKycIdAndReviewResult(kycId, "REVIEW")
                    .orElseThrow(() -> new NoSuchElementException("Pending low-confidence review not found: " + kycId))
                    .recordManualDecision(decision.name(), comment, decidedAt);
            default -> throw new IllegalArgumentException("Unknown review source: " + request.source());
        }

        record.setStatus(decision == Decision.ACCEPTED ? "VERIFIED" : "FAILED");
        record.setDecisionSource("MANUAL");
        record.markUpdatedAt(decidedAt);
        orchestrator.applicationStatusUpdate(record.getApplicationId(), decision, comment);
    }

    private ReviewQueueView toReviewQueueView(QueueCandidate candidate,
                                               Map<String, KycRecord> recordsByKycId) {
        KycRecord record = recordsByKycId.get(candidate.kycId());
        if (record == null) {
            throw new IllegalStateException("Review record has no KYC record: " + candidate.kycId());
        }
        return new ReviewQueueView(record.getApplicationId(), candidate.kycId(), candidate.source(),
                candidate.createdAt(), candidate.reviewResult(), candidate.confidence(),
                candidate.comment());
    }

    private KycAssessment assess(ApplicationRequest request) {
        Application application = required(request.application(), "application");
        Application.Applicant applicant = required(application.applicant(), "application.applicant");
        Application.IdentityDocument document =
                required(application.identityDocument(), "application.identityDocument");
        LocalDate expiryDate = parseDate(required(
            document.expiryDate(), "application.identityDocument.expiryDate"),
            "application.identityDocument.expiryDate");
        String documentType = required(document.type(), "application.identityDocument.type");
        String issuingCountry = required(document.issuingCountry(),
            "application.identityDocument.issuingCountry").trim();
        boolean expiresTooSoon = expiryDate.isBefore(LocalDate.now(clock).plusMonths(6));
        String kycId = UUID.randomUUID().toString();

        VerificationOutcome outcome;
        if (!isIsoCountryCode(issuingCountry)) {
            outcome = new VerificationOutcome(
                "FAILED",
                Decision.REJECTED,
                "application.identityDocument.issuingCountry has invalid country code: "
                    + issuingCountry,
                List.of(),
                null);
        } else if (expiresTooSoon) {
            outcome = new VerificationOutcome(
                "FAILED",
                Decision.REJECTED,
                "identity document expires in less than 6 months",
                List.of(),
                null);
        } else {
            outcome = verifyIdentityDocument(kycId, documentType);
        }

        KycRecord record = new KycRecord(
                kycId,
                request.applicationId(),
                outcome.status(),
            "AUTO",
                required(applicant.fullName(), "application.applicant.fullName"),
                documentType,
                required(document.documentId(), "application.identityDocument.documentId"),
            issuingCountry,
                expiryDate);
        ReviewFail reviewFail = outcome.decision() == Decision.REFERRED
                && outcome.reviewConfidence() == null
                ? new ReviewFail(UUID.randomUUID().toString(), kycId, null, "REVIEW", null)
                : null;
        ReviewScore reviewScore = outcome.reviewConfidence() == null
                ? null
                : new ReviewScore(UUID.randomUUID().toString(), kycId, null,
                outcome.reviewConfidence(), "REVIEW", null);
        return new KycAssessment(record, outcome.decision(), outcome.comment(), outcome.attempts(),
                reviewFail, reviewScore);
    }

    private VerificationOutcome verifyIdentityDocument(String kycId, String documentType) {
        return switch (documentType.toUpperCase(Locale.ROOT)) {
            case "PASSPORT" -> verifyWithRetries(kycId, documentType,
                    idVerificationClient::verifyPassport);
            case "NATIONAL_ID" -> verifyWithRetries(kycId, documentType,
                idVerificationClient::verifyNationalId);
            case "DRIVING_LICENCE" -> verifyWithRetries(kycId, documentType,
                    idVerificationClient::verifyDrivingLicense);
            default -> new VerificationOutcome(
                    "VERIFIED",
                    Decision.ACCEPTED,
                    "identity document verified",
                    List.of(),
                    null);
        };
    }

    private VerificationOutcome verifyWithRetries(String kycId,
                                                  String documentType,
                                                  VerificationCall verificationCall) {
        List<ThirdPartyAttempt> attempts = new ArrayList<>();
        for (int attemptNumber = 1; attemptNumber <= MAX_THIRD_PARTY_ATTEMPTS; attemptNumber++) {
            Integer confidence = verificationCall.verify();
            AttemptOutcome attemptOutcome = classifyAttempt(confidence);
            attempts.add(new ThirdPartyAttempt(
                    UUID.randomUUID().toString(),
                    kycId,
                    attemptNumber,
                    documentType,
                attemptOutcome.result(),
                attemptOutcome.storeConfidence() ? confidence : null,
                attemptComment(documentType, attemptOutcome)));

            switch (attemptOutcome) {
            case ACCEPTED -> {
                return new VerificationOutcome(
                    "VERIFIED",
                    Decision.ACCEPTED,
                    "%s verified on attempt %d (confidence %d)".formatted(
                        documentType.toLowerCase(Locale.ROOT), attemptNumber, confidence),
                    attempts,
                    null);
            }
            case REVIEW -> {
                return new VerificationOutcome(
                    "REVIEW",
                    Decision.REFERRED,
                    "%s requires manual review on attempt %d (confidence %d)".formatted(
                        documentType.toLowerCase(Locale.ROOT), attemptNumber, confidence),
                    attempts,
                    confidence);
            }
            case REJECTED -> {
                return new VerificationOutcome(
                    "FAILED",
                    Decision.REJECTED,
                    "%s verification failed on attempt %d (confidence %d)".formatted(
                        documentType.toLowerCase(Locale.ROOT), attemptNumber, confidence),
                    attempts,
                    null);
            }
            case UNAVAILABLE -> {
                if (attemptNumber < MAX_THIRD_PARTY_ATTEMPTS) {
                backoff(attemptNumber);
                }
            }
            }
        }

        return new VerificationOutcome(
                "REVIEW",
                Decision.REFERRED,
                "%s verification unavailable after %d attempts; manual review required".formatted(
                        documentType.toLowerCase(Locale.ROOT), MAX_THIRD_PARTY_ATTEMPTS),
                attempts,
                null);
    }

    private void backoff(int attemptNumber) {
        long delayMillis = INITIAL_BACKOFF_MILLIS << (attemptNumber - 1);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("third-party retry interrupted", e);
        }
    }

    private AttemptOutcome classifyAttempt(Integer confidence) {
        if (confidence == null || confidence < 0) {
            return AttemptOutcome.UNAVAILABLE;
        }
        if (confidence >= ACCEPT_CONFIDENCE_MIN) {
            return AttemptOutcome.ACCEPTED;
        }
        if (confidence >= REVIEW_CONFIDENCE_MIN) {
            return AttemptOutcome.REVIEW;
        }
        return AttemptOutcome.REJECTED;
    }

    private String attemptComment(String documentType, AttemptOutcome outcome) {
        String normalizedType = documentType.toLowerCase(Locale.ROOT);
        return switch (outcome) {
            case ACCEPTED -> "%s verification succeeded".formatted(normalizedType);
            case REVIEW -> "%s verification needs review".formatted(normalizedType);
            case REJECTED -> "%s verification failed".formatted(normalizedType);
            case UNAVAILABLE -> "%s provider unavailable".formatted(normalizedType);
        };
    }

    private LocalDate parseDate(String rawValue, String field) {
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DAY_MONTH_YEAR)) {
            try {
                return LocalDate.parse(rawValue, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported wire format.
            }
        }
        throw new IllegalArgumentException(field + " has invalid date format: " + rawValue);
    }

    private boolean isIsoCountryCode(String rawValue) {
        return rawValue.length() == 2
                && rawValue.chars().allMatch(character -> character >= 'A' && character <= 'Z');
    }

    private static <T> T required(T value, String field) {
        if (value == null || value instanceof String string && string.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private interface VerificationCall {
        Integer verify();
    }

    private enum AttemptOutcome {
        ACCEPTED("SUCCESS", true),
        REVIEW("REVIEW", true),
        REJECTED("FAILED", true),
        UNAVAILABLE("UNAVAILABLE", false);

        private final String result;
        private final boolean storeConfidence;

        AttemptOutcome(String result, boolean storeConfidence) {
            this.result = result;
            this.storeConfidence = storeConfidence;
        }

        private String result() {
            return result;
        }

        private boolean storeConfidence() {
            return storeConfidence;
        }
    }

    private record VerificationOutcome(String status,
                                       Decision decision,
                                       String comment,
                                       List<ThirdPartyAttempt> attempts,
                                       Integer reviewConfidence) {
    }

    private record KycAssessment(KycRecord record,
                                 Decision decision,
                                 String comment,
                                 List<ThirdPartyAttempt> attempts,
                                 ReviewFail reviewFail,
                                 ReviewScore reviewScore) {
    }

    private record QueueCandidate(
            String reviewId,
            String kycId,
            String source,
            Instant createdAt,
            String reviewResult,
            Integer confidence,
            String comment) {

        private static QueueCandidate from(ReviewFail review) {
            return new QueueCandidate(review.getReviewFailId(), review.getKycId(), "FAIL",
                    review.getCreatedAt(), review.getReviewResult(), null,
                    review.getManualReviewComment());
        }

        private static QueueCandidate from(ReviewScore review) {
            return new QueueCandidate(review.getReviewScoreId(), review.getKycId(), "SCORE",
                    review.getCreatedAt(), review.getReviewResult(), review.getConfidence(),
                    review.getManualReviewComment());
        }
    }
}
