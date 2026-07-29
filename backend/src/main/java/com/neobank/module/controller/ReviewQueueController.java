package com.neobank.module.controller;

import com.neobank.module.dto.ReviewQueueView;
import com.neobank.module.service.ApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
}
