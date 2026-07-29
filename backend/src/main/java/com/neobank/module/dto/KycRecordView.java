package com.neobank.module.dto;

import com.neobank.module.model.KycRecord;
import java.time.Instant;
import java.time.LocalDate;

public record KycRecordView(
        String kycId,
        String applicationId,
        String status,
    String decisionSource,
        String name,
        String type,
        String documentId,
        String issuingCountry,
        LocalDate expiryDate,
        Instant createdAt,
        Instant updatedAt,
        /** The locked KYC_* code behind the outcome. Null on rows decided before change set 009. */
        String reasonCode) {

    public static KycRecordView of(KycRecord row) {
        return new KycRecordView(
                row.getKycId(),
                row.getApplicationId(),
                row.getStatus(),
            row.getDecisionSource(),
                row.getName(),
                row.getType(),
                row.getDocumentId(),
                row.getIssuingCountry(),
                row.getExpiryDate(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getReasonCode());
    }
}
