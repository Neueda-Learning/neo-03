package com.neobank.module.service;

import com.neobank.module.dto.KycRecordView;
import com.neobank.module.dto.ManualReviewDecisionRequest;
import com.neobank.module.dto.ReviewQueueView;
import com.neobank.module.dto.ThirdPartyAttemptView;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Value;
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
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd-MM-uuuu");

    private final Executor executor;
    private final KycRecordRepository kycRecords;
    private final ThirdPartyAttemptRepository thirdPartyAttempts;
    private final ReviewFailRepository reviewFails;
    private final ReviewScoreRepository reviewScores;
    private final OrchestratorClient orchestrator;
    private final ProviderGateway gateway;
    private final Clock clock;

    /**
     * Confidence at or above this verifies the applicant.
     *
     * <p>Configuration, not a constant, because <b>thresholds are compliance policy</b>. When the
     * risk team wants 95 instead of 92 that is a decision about appetite, and it should not need a
     * developer, a code review and a deploy. {@code >=} and not {@code >}: a document scoring
     * exactly the accept threshold passes, which is the boundary the module brief singles out.</p>
     */
    private final int acceptThreshold;

    /**
     * Confidence at or below this rejects. Strictly between the two → a human decides.
     *
     * <p>{@code <=}, mirroring accept, so the two boundaries are inclusive on their own side and
     * there is no score that falls through both tests.</p>
     */
    private final int rejectThreshold;

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
                              ProviderGateway gateway,
                              Clock clock,
                              @Value("${id-provider.accept-threshold:92}") int acceptThreshold,
                              @Value("${id-provider.reject-threshold:60}") int rejectThreshold) {
        this.executor = executor;
        this.kycRecords = kycRecords;
        this.thirdPartyAttempts = thirdPartyAttempts;
        this.reviewFails = reviewFails;
        this.reviewScores = reviewScores;
        this.orchestrator = orchestrator;
        this.gateway = gateway;
        this.clock = clock;
        if (rejectThreshold >= acceptThreshold) {
            // Caught at startup rather than per application. Inverted thresholds do not throw at
            // decision time — they quietly make the REVIEW band empty, so every borderline case
            // silently passes or fails and nothing ever reaches a human.
            throw new IllegalArgumentException(
                    "reject threshold (%d) must be below accept threshold (%d)"
                            .formatted(rejectThreshold, acceptThreshold));
        }
        this.acceptThreshold = acceptThreshold;
        this.rejectThreshold = rejectThreshold;
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

    /** Everything this module has answered, newest first — what its own UI reads. */
    @Transactional(readOnly = true)
    public List<KycRecordView> findAll() {
        return kycRecords.findAllByOrderByCreatedAtDescKycIdDesc().stream()
                .map(KycRecordView::of)
                .toList();
    }

    /**
     * Every provider call made for one case, oldest first — the evidence behind its outcome.
     *
     * <p><b>An unknown case is a 404; a known case with no attempts is an empty list.</b> Those are
     * different facts and the screen shows them differently. Zero attempts is not a gap in the
     * data: both local pre-checks decide without calling anyone, and the empty list is the proof
     * that no provider fee was paid for an answer the document itself already gave us.</p>
     */
    @Transactional(readOnly = true)
    public List<ThirdPartyAttemptView> findAttempts(String kycId) {
        // Look the case up first. Returning [] for an id that does not exist would tell an
        // operator "this case made no calls" about a case that is not there at all.
        kycRecords.findById(kycId)
                .orElseThrow(() -> new NoSuchElementException("KYC record not found: " + kycId));

        return thirdPartyAttempts.findByKycIdOrderByAttemptNumberAsc(kycId).stream()
                .map(ThirdPartyAttemptView::of)
                .toList();
    }

    /** The complete manual-review queue, oldest first across both review tables. */
    @Transactional(readOnly = true)
    public List<ReviewQueueView> findEarliestReviewQueue() {
        List<QueueCandidate> candidates = Stream.concat(
                        reviewFails.findByReviewResultOrderByCreatedAtAscReviewFailIdAsc("REVIEW").stream()
                                .map(QueueCandidate::from),
                        reviewScores.findByReviewResultOrderByCreatedAtAscReviewScoreIdAsc("REVIEW").stream()
                                .map(QueueCandidate::from))
                .sorted(Comparator.comparing(QueueCandidate::createdAt)
                        .thenComparing(QueueCandidate::source)
                        .thenComparing(QueueCandidate::reviewId))
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

        // TWO local pre-checks, then the provider — and when either fires the provider is never
        // called at all. That is not an optimisation: the empty attempt list each one leaves
        // behind is the evidence that no provider fee was paid for an answer we already had.
        VerificationOutcome outcome;
        if (!isIsoCountryCode(issuingCountry)) {
            // The document as presented cannot be looked up anywhere, so KYC_DOCUMENT_INVALID —
            // the nearest of the six locked codes. Do not invent a seventh for this; ask first.
            outcome = new VerificationOutcome(
                    "FAILED",
                    Decision.REJECTED,
                    ReasonCode.KYC_DOCUMENT_INVALID,
                    comment(ReasonCode.KYC_DOCUMENT_INVALID,
                            "issuing country '" + issuingCountry + "' is not an ISO alpha-2 code"),
                    List.of(),
                    null);
        } else if (expiresTooSoon) {
            outcome = new VerificationOutcome(
                    "FAILED",
                    Decision.REJECTED,
                    ReasonCode.KYC_DOCUMENT_EXPIRED,
                    comment(ReasonCode.KYC_DOCUMENT_EXPIRED,
                            "identity document expires in less than 6 months (" + expiryDate + ")"),
                    List.of(),
                    null);
        } else {
            outcome = verifyIdentityDocument(kycId, application);
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
                expiryDate,
                outcome.reason().name());
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

    /**
     * Ask the identity sources, then turn what they said into this module's answer.
     *
     * <p>The ladder, the backoff, the failover and the circuit breaker all live in
     * {@link ProviderGateway}. What is left here is the only part that is a BANKING decision:
     * which confidence means what.</p>
     */
    private VerificationOutcome verifyIdentityDocument(String kycId, Application application) {
        ProviderGateway.ProviderOutcome provider = gateway.verify(kycId, application);

        if (!provider.answered()) {
            // Nobody answered. This is REVIEW and never FAILED: "rejected" is a business statement
            // about the applicant, and a provider outage says nothing whatsoever about them. The
            // applicant did nothing wrong, so a human picks it up.
            return new VerificationOutcome("REVIEW", Decision.REFERRED,
                    ReasonCode.KYC_PROVIDER_UNAVAILABLE,
                    comment(ReasonCode.KYC_PROVIDER_UNAVAILABLE,
                            "no identity source answered after %d attempts (%s)"
                                    .formatted(provider.attempts().size(), provider.lastFailure())),
                    provider.attempts(), null);
        }

        int confidence = provider.answer().confidence();

        // The forgery check comes BEFORE the confidence bands. A document the register says is not
        // genuine is refused whatever number sits beside it — a convincing forgery scores well,
        // which is precisely what makes it a forgery.
        if (provider.answer().documentReportedForged()) {
            return new VerificationOutcome("FAILED", Decision.REJECTED,
                    ReasonCode.KYC_DOCUMENT_INVALID,
                    comment(ReasonCode.KYC_DOCUMENT_INVALID, provider,
                            "the issuing register does not recognise this document"),
                    provider.attempts(), null);
        }

        if (confidence >= acceptThreshold) {
            return new VerificationOutcome("VERIFIED", Decision.ACCEPTED,
                    ReasonCode.KYC_VERIFIED,
                    comment(ReasonCode.KYC_VERIFIED, provider,
                            "identity confirmed with confidence %d".formatted(confidence)),
                    provider.attempts(), null);
        }

        if (confidence <= rejectThreshold) {
            return new VerificationOutcome("FAILED", Decision.REJECTED,
                    ReasonCode.KYC_LOW_CONFIDENCE,
                    comment(ReasonCode.KYC_LOW_CONFIDENCE, provider,
                            "confidence %d is at or below the reject threshold %d"
                                    .formatted(confidence, rejectThreshold)),
                    provider.attempts(), null);
        }

        // Strictly between the thresholds. Not confident enough to pass, not doubtful enough to
        // refuse — which is exactly the case a human should look at, and the reason this module has
        // three outcomes rather than two.
        return new VerificationOutcome("REVIEW", Decision.REFERRED,
                ReasonCode.KYC_LOW_CONFIDENCE,
                comment(ReasonCode.KYC_LOW_CONFIDENCE, provider,
                        "confidence %d sits between the reject (%d) and accept (%d) thresholds"
                                .formatted(confidence, rejectThreshold, acceptThreshold)),
                provider.attempts(), confidence);
    }

    /**
     * The callback comment: a locked reason code, then the same fact in words.
     *
     * <p>Both halves earn their place. The code is what the console filters and the regulator's
     * export groups by; the sentence is what an operator reads out to a customer who is asking why.
     * The wire has three fields and no {@code reasons[]} array, so this is where a code can live —
     * see {@link ReasonCode}.</p>
     */
    private String comment(ReasonCode code, String detail) {
        return code + " · " + detail;
    }

    /**
     * As above, plus {@code KYC_FAILED_OVER_TO_SECONDARY} when the fallback is what answered.
     *
     * <p>Appended alongside the outcome code rather than replacing it: the applicant's result and
     * the route it took there are two different facts, and an operator reviewing a case needs to
     * know that this verdict came from a source that never saw the document.</p>
     */
    private String comment(ReasonCode code, ProviderGateway.ProviderOutcome provider, String detail) {
        String base = comment(code, detail);
        return provider.failedOver() ? base + " · " + ReasonCode.KYC_FAILED_OVER_TO_SECONDARY : base;
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

    /**
     * @param reason  the locked code behind this outcome. Carried as a field rather than parsed
     *                back out of {@code comment} — the comment is prose for a human and its shape
     *                is free to change, so reading a code out of it would make the record depend
     *                on the wording.
     */
    private record VerificationOutcome(String status,
                                       Decision decision,
                                       ReasonCode reason,
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
