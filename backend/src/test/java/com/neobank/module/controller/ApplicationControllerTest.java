package com.neobank.module.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.dto.KycRecordView;
import com.neobank.module.service.ApplicationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the inbound wire — the JSON the orchestrator parses, and what this module tolerates.
 *
 * <p><b>Most of these tests exist to stop the {@code Application} model being tightened.</b> Typed
 * dates and enums look like better modelling and would turn three of the sidecar's scenarios into
 * {@code 400}s — scenarios that exist specifically to check the module <em>reports</em> bad input
 * rather than refusing it. If one of these fails after you change {@code Application}, the model is
 * wrong, not the test.</p>
 */
@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ApplicationService applications;

    @Test
    void acceptsAnApplicationAndHandsItToTheService() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId": "SIM-01",
                                  "correlationId": "sim-0001",
                                  "command": "process-application",
                                  "application": {
                                    "channel": "MOBILE_APP",
                                    "applicant": {"fullName": "Maria Nowak"},
                                    "product": {"productCode": "CREDIT_CARD_REWARDS",
                                                "requestedCreditLimit": 3000}
                                  }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.applicationId").value("SIM-01"))
                .andExpect(jsonPath("$.serviceId").value("neo03"))
                .andExpect(jsonPath("$.command").value("process-application"));

        ArgumentCaptor<ApplicationRequest> sent = ArgumentCaptor.forClass(ApplicationRequest.class);
        verify(applications).processApplicationAsync(sent.capture());

        // The typed model really is populated, not silently swallowed into nulls.
        ApplicationRequest request = sent.getValue();
        assertThat(request.application().applicant().fullName()).isEqualTo("Maria Nowak");
        assertThat(request.application().product().requestedCreditLimit()).isEqualTo(3000);
        assertThat(request.application().channel()).isEqualTo("MOBILE_APP");
    }

    @Test
    void rejectsAnEnvelopeWithNoApplicationId() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"command":"process-application","application":{"channel":"WEB"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("applicationId")));

        verifyNoInteractions(applications);
    }

    @Test
    void malformedDatesAreAcceptedBecauseJudgingThemIsTheModulesJob() throws Exception {
        // The sidecar's SIM-09 shape: every date in the wrong format, on purpose. It must reach the
        // service so the module can report WHICH field was malformed. Typing these as LocalDate
        // would make Jackson answer 400 and the module would never see the application.
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId": "SIM-09",
                                  "command": "process-application",
                                  "application": {
                                    "applicant": {"dateOfBirth": "12/03/1990",
                                                  "email": "ines.dacosta(at)example.com",
                                                  "nationality": "PRT"},
                                    "identityDocument": {"expiryDate": "31-12-2030"}
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(applications).processApplicationAsync(any(ApplicationRequest.class));
    }

    @Test
    void anUnknownProductCodeIsAcceptedRatherThanRefusedAtTheDoor() throws Exception {
        // The sidecar's SIM-24 shape: a product outside the locked catalogue. An enum would reject
        // it here; the module is supposed to answer "that product does not exist".
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationId":"SIM-24","command":"process-application",
                                 "application":{"product":{"productCode":"CREDIT_CARD_PREMIUM"}}}
                                """))
                .andExpect(status().isAccepted());

        verify(applications).processApplicationAsync(any(ApplicationRequest.class));
    }

    @Test
    void unknownFieldsAreIgnoredAtEveryLevel() throws Exception {
        // The sidecar's SIM-23 shape: extra fields at the envelope, application, applicant and
        // product levels. The orchestrator must be able to add a field without breaking ten modules.
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId": "SIM-23",
                                  "command": "process-application",
                                  "referrer": "partner-site",
                                  "application": {
                                    "campaignCode": "SUMMER26",
                                    "applicant": {"fullName": "Maria Nowak", "middleName": "Anna"},
                                    "product": {"productCode": "CREDIT_CARD_REWARDS",
                                                "promoRate": 0.0}
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<ApplicationRequest> sent = ArgumentCaptor.forClass(ApplicationRequest.class);
        verify(applications).processApplicationAsync(sent.capture());
        assertThat(sent.getValue().application().applicant().fullName()).isEqualTo("Maria Nowak");
    }

    @Test
    void echoesANullCommandAsJsonNullRatherThanThrowing() throws Exception {
        mvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicationId":"SIM-26","application":{"channel":"WEB"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.command").doesNotExist());

        verify(applications).processApplicationAsync(any(ApplicationRequest.class));
    }

    @Test
    void returnsTheApplicationsBoardRows() throws Exception {
        when(applications.findAll()).thenReturn(List.of(new KycRecordView(
                "KYC-1",
                "SIM-01",
                "VERIFIED",
                "MANUAL",
                "Maria Nowak",
                "PASSPORT",
                "P1234567",
                "PL",
                LocalDate.parse("2031-02-28"),
                Instant.parse("2026-07-20T09:12:00Z"),
                Instant.parse("2026-07-21T10:15:00Z"))));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("SIM-01"))
                .andExpect(jsonPath("$[0].decisionSource").value("MANUAL"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-07-20T09:12:00Z"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-07-21T10:15:00Z"));
    }
}
