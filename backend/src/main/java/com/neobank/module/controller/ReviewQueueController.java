package com.neobank.module.controller;

import com.neobank.module.dto.ManualReviewDecisionRequest;
import com.neobank.module.dto.ReviewQueueView;
import com.neobank.module.service.ApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only API for the KYC analyst's oldest-first review queue. */
@RestController
@RequestMapping("/api/v1/review-queue")
public class ReviewQueueController {

    private final ApplicationService applications;

    public ReviewQueueController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public List<ReviewQueueView> list() {
        return applications.findEarliestReviewQueue();
    }

    @PostMapping("/{kycId}/decision")
    public ResponseEntity<Void> recordDecision(
            @PathVariable String kycId,
            @Valid @RequestBody ManualReviewDecisionRequest request) {
        applications.recordManualReviewDecision(kycId, request);
        return ResponseEntity.noContent().build();
    }
}
