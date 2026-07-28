package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "review_score")
public class ReviewScore {

    @Id
    @Column(name = "review_score_id", nullable = false, length = 64)
    private String reviewScoreId;

    @Column(name = "kyc_id", nullable = false, length = 64)
    private String kycId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column
    private Integer confidence;

    @Column(name = "review_result", length = 32)
    private String reviewResult;

    @Column(length = 1000)
    private String comment;

    protected ReviewScore() {
        // JPA
    }

    public ReviewScore(String reviewScoreId, String kycId, Instant updatedAt, Integer confidence,
                       String reviewResult, String comment) {
        this.reviewScoreId = reviewScoreId;
        this.kycId = kycId;
        this.updatedAt = updatedAt;
        this.confidence = confidence;
        this.reviewResult = reviewResult;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getReviewScoreId() {
        return reviewScoreId;
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

    public Integer getConfidence() {
        return confidence;
    }

    public String getReviewResult() {
        return reviewResult;
    }

    public String getComment() {
        return comment;
    }
}
