package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "review_third_party_fail")
public class ReviewFail {

    @Id
    @Column(name = "review_fail_id", nullable = false, length = 64)
    private String reviewFailId;

    @Column(name = "kyc_id", nullable = false, length = 64)
    private String kycId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "review_result", length = 32)
    private String reviewResult;

    @Column(name = "manual_review_comment", length = 1000)
    private String manualReviewComment;

    protected ReviewFail() {
        // JPA
    }

    public ReviewFail(String reviewFailId, String kycId, Instant updatedAt, String reviewResult,
                      String manualReviewComment) {
        this.reviewFailId = reviewFailId;
        this.kycId = kycId;
        this.updatedAt = updatedAt;
        this.reviewResult = reviewResult;
        this.manualReviewComment = manualReviewComment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getReviewFailId() {
        return reviewFailId;
    }

    public String getKycId() {
        return kycId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getReviewResult() {
        return reviewResult;
    }

    public String getManualReviewComment() {
        return manualReviewComment;
    }

    public void recordManualDecision(String reviewResult, String comment, Instant decidedAt) {
        this.reviewResult = reviewResult;
        this.manualReviewComment = comment;
        this.updatedAt = decidedAt;
    }
}
