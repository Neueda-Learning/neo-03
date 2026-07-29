package com.neobank.module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The analyst's final decision for one pending review record. */
public record ManualReviewDecisionRequest(
        @NotBlank @Pattern(regexp = "FAIL|SCORE") String source,
        @NotBlank @Pattern(regexp = "ACCEPTED|REJECTED") String decision,
        @NotBlank @Size(max = 1000) String comment) {
}
