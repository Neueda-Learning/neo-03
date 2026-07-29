package com.neobank.module.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "kyc_record")
public class KycRecord {

    @Id
    @Column(name = "kyc_id", nullable = false, length = 64)
    private String kycId;

    @Column(name = "application_id", nullable = false, length = 64)
    private String applicationId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "decision_source", nullable = false, length = 16)
    private String decisionSource;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "document_id", nullable = false, length = 128)
    private String documentId;

    // Wider than the two characters the contract's ground rule promises, and deliberately so:
    // the corpus contains an application carrying "PRT". At length 2 that insert throws in MySQL
    // strict mode, so the case is lost entirely and the applicant gets a stack trace as an
    // explanation — a module has to be able to STORE a malformed value in order to report which
    // field was wrong.
    //
    // Widened to 16 in changeset 006. `nullable = false` is only true of the database because
    // changeset 008 puts the constraint back: MySQL's MODIFY replaces the whole column
    // definition, so 006 dropped it silently, and Hibernate's validate does not check
    // nullability and would never have told us.
    @Column(name = "issuing_country", nullable = false, length = 16)
    private String issuingCountry;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KycRecord() {
    }

    public KycRecord(String kycId,
                     String applicationId,
                     String status,
                     String decisionSource,
                     String name,
                     String type,
                     String documentId,
                     String issuingCountry,
                     LocalDate expiryDate) {
        this.kycId = kycId;
        this.applicationId = applicationId;
        this.status = status;
        this.decisionSource = decisionSource;
        this.name = name;
        this.type = type;
        this.documentId = documentId;
        this.issuingCountry = issuingCountry;
        this.expiryDate = expiryDate;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getKycId() {
        return kycId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDecisionSource() {
        return decisionSource;
    }

    public void setDecisionSource(String decisionSource) {
        this.decisionSource = decisionSource;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getIssuingCountry() {
        return issuingCountry;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
