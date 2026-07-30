package com.neobank.mockagency.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neobank.mockagency.config.AppConfig;
import com.neobank.mockagency.service.AgencyService;
import com.neobank.mockagency.service.ConfidenceBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminController.class, VerificationController.class})
@Import({AgencyService.class, ConfidenceBook.class, AppConfig.class, GlobalExceptionHandler.class})
class AdminControllerTest {

    private static final String APPLICATION = """
            {"fullName":"Maria Nowak","dateOfBirth":"1996-04-11",
             "document":{"type":"PASSPORT","documentId":"ZS1234567",
                         "issuingCountry":"PL","expiryDate":"2031-02-28"}}""";

    @Autowired
    private MockMvc mockMvc;

    /**
     * The dials and the call counters live in a singleton bean, and Spring caches the test context
     * across methods — so without this, one test's kill switch is the next test's mystery 503 and
     * the counter assertions depend on JUnit's method order. Reset is the service's own escape
     * hatch, so using it here also exercises it.
     */
    @Autowired
    private AgencyService agencies;

    @BeforeEach
    void resetDials() {
        agencies.reset();
    }

    @Test
    @DisplayName("GET /admin/config reports both agencies, healthy, with zero calls")
    void configStartsHealthy() throws Exception {
        mockMvc.perform(get("/api/v1/admin/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencies.national.killSwitch").value(false))
                .andExpect(jsonPath("$.agencies.national.latencyMs").value(0))
                .andExpect(jsonPath("$.agencies.tax.failureRatePct").value(0))
                .andExpect(jsonPath("$.agencies.national.answerMode").value("NORMAL"))
                .andExpect(jsonPath("$.calls.national").value(0));
    }

    @Test
    @DisplayName("A kill switch set through the API is honoured by the next verification")
    void killSwitchTakesEffectOnTheNextCall() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/national")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latencyMs":0,"failureRatePct":0,"killSwitch":true}"""))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/agencies/national/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
                .andExpect(status().isServiceUnavailable());

        // The fallback is untouched — one PUT is the whole failover demo.
        mockMvc.perform(post("/api/v1/agencies/tax/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value(92));
    }

    @Test
    @DisplayName("Reset undoes it — the demo is reversible live")
    void resetRestoresHealthy() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/national")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"latencyMs":5000,"failureRatePct":100,"killSwitch":true}"""));

        mockMvc.perform(post("/api/v1/admin/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencies.national.killSwitch").value(false))
                .andExpect(jsonPath("$.agencies.national.latencyMs").value(0));

        mockMvc.perform(post("/api/v1/agencies/national/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value(92));
    }

    @Test
    @DisplayName("A failure rate above 100 is a field-level 400, not a silently clamped value")
    void outOfRangeFailureRateIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/national")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latencyMs":0,"failureRatePct":140,"killSwitch":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("failureRatePct")));
    }

    @Test
    @DisplayName("A negative latency is a field-level 400")
    void negativeLatencyIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/national")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latencyMs":-1,"failureRatePct":0,"killSwitch":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("latencyMs")));
    }

    @Test
    @DisplayName("Dialling an unknown agency is a 404")
    void unknownAgencyIs404() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/interpol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latencyMs":0,"failureRatePct":0,"killSwitch":false}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Call counts move, which is how a failover is visible on the panel")
    void callCountsTrackVerifications() throws Exception {
        mockMvc.perform(post("/api/v1/agencies/national/verifications")
                .contentType(MediaType.APPLICATION_JSON).content(APPLICATION));
        mockMvc.perform(post("/api/v1/agencies/tax/verifications")
                .contentType(MediaType.APPLICATION_JSON).content(APPLICATION));
        mockMvc.perform(post("/api/v1/agencies/tax/verifications")
                .contentType(MediaType.APPLICATION_JSON).content(APPLICATION));

        mockMvc.perform(get("/api/v1/admin/config"))
                .andExpect(jsonPath("$.calls.national").value(1))
                .andExpect(jsonPath("$.calls.tax").value(2));
    }

    @Test
    @DisplayName("A mode set through the API changes what the next verification answers")
    void answerModeIsSettableOverHttp() {
        // The whole point of the module proxying this endpoint: on AWS the mock has no public
        // route, so if it cannot be driven over HTTP it cannot be driven at all.
        try {
            mockMvc.perform(put("/api/v1/admin/config/national")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"latencyMs":0,"failureRatePct":0,"killSwitch":false,
                                     "answerMode":"ALL_FAIL"}"""))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/agencies/national/verifications")
                            .contentType(MediaType.APPLICATION_JSON).content(APPLICATION))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.confidence").value(org.hamcrest.Matchers.lessThanOrEqualTo(60)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("An unknown mode is a 400, not a silent fall back to NORMAL")
    void anUnknownModeIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/config/national")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latencyMs":0,"failureRatePct":0,"killSwitch":false,
                                 "answerMode":"ALL_MAYBE"}"""))
                .andExpect(status().isBadRequest());
    }
}
