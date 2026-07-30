package com.neobank.mockagency.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neobank.mockagency.config.AppConfig;
import com.neobank.mockagency.service.AgencyService;
import com.neobank.mockagency.service.ConfidenceBook;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The wire, over HTTP. {@link com.neobank.mockagency.service.AgencyServiceTest} covers the
 * behaviour; this pins the JSON the module actually parses — field names included, because a
 * renamed field is a silent null on the other side, not a failure.
 */
@WebMvcTest(VerificationController.class)
@Import({AgencyService.class, ConfidenceBook.class, AppConfig.class, GlobalExceptionHandler.class})
class VerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper json = new ObjectMapper();

    private String body(String documentId) throws Exception {
        return json.writeValueAsString(Map.of(
                "fullName", "Maria Nowak",
                "dateOfBirth", "1996-04-11",
                "address", Map.of("line1", "12 Ulica Kwiatowa", "city", "Warsaw",
                        "postcode", "00-001", "country", "PL"),
                "document", Map.of("type", "PASSPORT", "documentId", documentId,
                        "issuingCountry", "PL", "expiryDate", "2031-02-28")));
    }

    @Test
    @DisplayName("POST /agencies/national/verifications answers the pinned shape")
    void nationalAnswersThePinnedShape() throws Exception {
        mockMvc.perform(post("/api/v1/agencies/national/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(body("ZS1234567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value(92))
                .andExpect(jsonPath("$.agency").value("NATIONAL_IDENTITY_AGENCY"))
                .andExpect(jsonPath("$.providerRef").exists())
                .andExpect(jsonPath("$.checkedAt").exists())
                .andExpect(jsonPath("$.checks.length()").value(4))
                .andExpect(jsonPath("$.checks[0].name").value("documentGenuine"))
                .andExpect(jsonPath("$.checks[0].passed").value(true));
    }

    @Test
    @DisplayName("POST /agencies/tax/verifications answers the same score, three checks")
    void taxAnswersTheSameScoreWithThreeChecks() throws Exception {
        mockMvc.perform(post("/api/v1/agencies/tax/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(body("ZS1234567")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value(92))
                .andExpect(jsonPath("$.agency").value("TAX_AGENCY"))
                .andExpect(jsonPath("$.checks.length()").value(3))
                .andExpect(jsonPath("$.checks[0].name").value("nameMatched"));
    }

    @Test
    @DisplayName("ZZ0000000 answers 503 on both agencies — the corpus's outage fixture")
    void corpusFailureDocumentAnswers503() throws Exception {
        for (String slug : new String[] {"national", "tax"}) {
            mockMvc.perform(post("/api/v1/agencies/" + slug + "/verifications")
                            .contentType(MediaType.APPLICATION_JSON).content(body("ZZ0000000")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Test
    @DisplayName("An unknown agency is a 404, not a silent fallback to a known one")
    void unknownAgencyIs404() throws Exception {
        mockMvc.perform(post("/api/v1/agencies/interpol/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(body("ZS1234567")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A missing documentId names the field it is missing")
    void missingDocumentIdIsAFieldLevel400() throws Exception {
        String noDocumentId = json.writeValueAsString(Map.of(
                "fullName", "Maria Nowak",
                "dateOfBirth", "1996-04-11",
                "document", Map.of("type", "PASSPORT", "issuingCountry", "PL")));

        mockMvc.perform(post("/api/v1/agencies/national/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(noDocumentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("documentId")));
    }

    @Test
    @DisplayName("A malformed date is NOT rejected — the mock answers and lets the bank decide")
    void malformedDatesStillGetAnAnswer() throws Exception {
        // SIM-09 carries expiryDate "31-12-2030". Dates are String on both sides on purpose: a
        // provider that 400s on a bad date takes away the bank's chance to say which field is wrong.
        String reversedDate = json.writeValueAsString(Map.of(
                "fullName", "Ines Da Costa",
                "dateOfBirth", "12/03/1990",
                "document", Map.of("type", "PASSPORT", "documentId", "PT2210984",
                        "issuingCountry", "PRT", "expiryDate", "31-12-2030")));

        mockMvc.perform(post("/api/v1/agencies/national/verifications")
                        .contentType(MediaType.APPLICATION_JSON).content(reversedDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").isNumber());
    }
}
