package com.neobank.module.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.module.integrations.orchestrator.ApplicationRequest;
import com.neobank.module.dto.KycRecordView;
import com.neobank.module.dto.ThirdPartyAttemptView;
import com.neobank.module.service.ApplicationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
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
                Instant.parse("2026-07-21T10:15:00Z"),
                "KYC_VERIFIED")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value("SIM-01"))
                .andExpect(jsonPath("$[0].decisionSource").value("MANUAL"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-07-20T09:12:00Z"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-07-21T10:15:00Z"))
                .andExpect(jsonPath("$[0].reasonCode").value("KYC_VERIFIED"));
    }

    @Test
    void returnsTheLadderBehindOneCase() throws Exception {
        // A full failover: three attempts against the primary, then one against the fallback that
        // answered. Every field is asserted because each one is the reason a column exists —
        // latency tells a timeout from a refusal, requestedAt spacing is the backoff, and agency
        // changing NATIONAL -> TAX is the only record that a failover happened at all.
        when(applications.findAttempts("KYC-1")).thenReturn(List.of(
                new ThirdPartyAttemptView(1, "NATIONAL", "TIMEOUT", null, 2004,
                        Instant.parse("2026-07-29T08:30:49Z"), null, "NATIONAL did not answer (TIMEOUT)"),
                new ThirdPartyAttemptView(2, "NATIONAL", "TIMEOUT", null, 2003,
                        Instant.parse("2026-07-29T08:30:52Z"), null, "NATIONAL did not answer (TIMEOUT)"),
                new ThirdPartyAttemptView(3, "NATIONAL", "SHORT_CIRCUITED", null, 0,
                        Instant.parse("2026-07-29T08:30:56Z"), null, "breaker open — provider not called"),
                new ThirdPartyAttemptView(4, "TAX", "ANSWERED", 92, 121,
                        Instant.parse("2026-07-29T08:30:58Z"), "tax-7f3a91c2", "answered with confidence 92")));

        mvc.perform(get("/api/v1/applications/KYC-1/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].attemptNumber").value(1))
                .andExpect(jsonPath("$[0].agency").value("NATIONAL"))
                .andExpect(jsonPath("$[0].result").value("TIMEOUT"))
                .andExpect(jsonPath("$[0].confidence").doesNotExist())
                .andExpect(jsonPath("$[0].latencyMs").value(2004))
                .andExpect(jsonPath("$[0].requestedAt").value("2026-07-29T08:30:49Z"))
                .andExpect(jsonPath("$[0].providerRef").doesNotExist())
                .andExpect(jsonPath("$[2].result").value("SHORT_CIRCUITED"))
                .andExpect(jsonPath("$[3].agency").value("TAX"))
                .andExpect(jsonPath("$[3].result").value("ANSWERED"))
                .andExpect(jsonPath("$[3].confidence").value(92))
                .andExpect(jsonPath("$[3].providerRef").value("tax-7f3a91c2"))
                .andExpect(jsonPath("$[3].comment").value("answered with confidence 92"))
                // The surrogate key and the kycId are deliberately not in the view.
                .andExpect(jsonPath("$[0].thirdPartyAttemptId").doesNotExist())
                .andExpect(jsonPath("$[0].kycId").doesNotExist());
    }

    @Test
    void aCaseDecidedWithoutTheProviderHasAnEmptyLadderRatherThanAnError() throws Exception {
        // An expired document, or an issuing country that is not a code, is decided locally and
        // the provider is never called. Zero attempts is the EVIDENCE of that, not a missing
        // resource — so it is 200 [] and the screen says "never called", not 404.
        when(applications.findAttempts("KYC-LOCAL")).thenReturn(List.of());

        mvc.perform(get("/api/v1/applications/KYC-LOCAL/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnknownCaseIs404() throws Exception {
        // The other half of the pair above: a case that does not exist must NOT read as a case
        // that made no calls.
        doThrow(new NoSuchElementException("KYC record not found: KYC-404"))
                .when(applications).findAttempts("KYC-404");

        mvc.perform(get("/api/v1/applications/KYC-404/attempts"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("KYC record not found: KYC-404"));
    }
}
