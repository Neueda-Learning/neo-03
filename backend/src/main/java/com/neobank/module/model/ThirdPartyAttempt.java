package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "third_party_attempts")
public class ThirdPartyAttempt {

    @Id
    @Column(name = "third_party_attempt_id", nullable = false, length = 64)
    private String thirdPartyAttemptId;

    @Column(name = "kyc_id", nullable = false, length = 64)
    private String kycId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "document_type", nullable = false, length = 64)
    private String documentType;

    @Column(nullable = false, length = 32)
    private String result;

    @Column
    private Integer confidence;

    @Column(length = 1000)
    private String comment;

    /**
     * Which identity source this call went to — {@code NATIONAL} or {@code TAX}.
     *
     * <p>Nullable because rows written before the module had a real provider have no answer to
     * this, and a NOT NULL column would have needed a made-up backfill value.</p>
     */
    @Column(length = 32)
    private String agency;

    /**
     * When the call went out.
     *
     * <p>Not the same as {@code created_at}, which is when the row was saved — all of a case's
     * attempts are saved together, in one batch, after the ladder finishes. So {@code created_at}
     * is identical across them and proves nothing, while the gaps between {@code requested_at}
     * values are what shows the backoff actually waited 1s and then 2s.</p>
     */
    @Column(name = "requested_at")
    private Instant requestedAt;

    /** Round-trip time of this call. A timeout shows up here as roughly the timeout budget. */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    /** The provider's own reference for the check, when it answered. */
    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ThirdPartyAttempt() {
    }

    public ThirdPartyAttempt(String thirdPartyAttemptId,
                             String kycId,
                             Integer attemptNumber,
                             String documentType,
                             String result,
                             Integer confidence,
                             String comment) {
        this(thirdPartyAttemptId, kycId, attemptNumber, documentType, result, confidence, comment,
                null, null, null, null);
    }

    public ThirdPartyAttempt(String thirdPartyAttemptId,
                             String kycId,
                             Integer attemptNumber,
                             String documentType,
                             String result,
                             Integer confidence,
                             String comment,
                             String agency,
                             Instant requestedAt,
                             Integer latencyMs,
                             String providerRef) {
        this.thirdPartyAttemptId = thirdPartyAttemptId;
        this.kycId = kycId;
        this.attemptNumber = attemptNumber;
        this.documentType = documentType;
        this.result = result;
        this.confidence = confidence;
        this.comment = comment;
        this.agency = agency;
        this.requestedAt = requestedAt;
        this.latencyMs = latencyMs;
        this.providerRef = providerRef;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getThirdPartyAttemptId() {
        return thirdPartyAttemptId;
    }

    public String getKycId() {
        return kycId;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getResult() {
        return result;
    }

    public Integer getConfidence() {
        return confidence;
    }

    public String getComment() {
        return comment;
    }

    public String getAgency() {
        return agency;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}