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

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "document_id", nullable = false, length = 128)
    private String documentId;

    @Column(name = "issuing_country", nullable = false, length = 2)
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
                     String name,
                     String type,
                     String documentId,
                     String issuingCountry,
                     LocalDate expiryDate) {
        this.kycId = kycId;
        this.applicationId = applicationId;
        this.status = status;
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
