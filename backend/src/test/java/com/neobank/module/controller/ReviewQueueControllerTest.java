package com.neobank.module.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.dto.ReviewQueueView;
import com.neobank.module.service.ApplicationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewQueueController.class)
class ReviewQueueControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ApplicationService applications;

    @Test
    void returnsTheReviewQueueForTheOperatorUi() throws Exception {
        when(applications.findEarliestReviewQueue()).thenReturn(List.of(new ReviewQueueView(
                "APP-1240",
                "KYC-1",
                "SCORE",
                Instant.parse("2026-07-20T09:12:00Z"),
                "REVIEW",
                74,
                "low confidence")));

        mvc.perform(get("/api/v1/review-queue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("APP-1240"))
                .andExpect(jsonPath("$[0].kycId").value("KYC-1"))
                .andExpect(jsonPath("$[0].source").value("SCORE"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-07-20T09:12:00Z"))
                .andExpect(jsonPath("$[0].reviewResult").value("REVIEW"))
                .andExpect(jsonPath("$[0].confidence").value(74))
                .andExpect(jsonPath("$[0].comment").value("low confidence"));
    }

    @Test
    void recordsAnAnalystDecisionForTheSelectedQueueEntry() throws Exception {
        mvc.perform(post("/api/v1/review-queue/KYC-1/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"SCORE","decision":"ACCEPTED",
                                 "comment":"Document checked by analyst"}
                                """))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(applications).recordManualReviewDecision(
                org.mockito.ArgumentMatchers.eq("KYC-1"),
                org.mockito.ArgumentMatchers.argThat(request ->
                        request.source().equals("SCORE")
                                && request.decision().equals("ACCEPTED")
                                && request.comment().equals("Document checked by analyst")));
    }
}
