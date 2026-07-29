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
        this.thirdPartyAttemptId = thirdPartyAttemptId;
        this.kycId = kycId;
        this.attemptNumber = attemptNumber;
        this.documentType = documentType;
        this.result = result;
        this.confidence = confidence;
        this.comment = comment;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}