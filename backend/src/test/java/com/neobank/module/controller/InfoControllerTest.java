package com.neobank.module.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InfoController.class)
@TestPropertySource(properties = {
        "service.id=neo03",
        "service.name=Identity Verification (KYC)",
        "service.team=Team 03",
        "service.domain=kyc",
        "service.version=0.1.0-SNAPSHOT",
        "service.orchestrator-url=http://localhost:9000",
        "service.mocked-dependencies=id-verification-provider, sanctions-list, , document-db",
        "id-provider.accept-threshold=95",
        "id-provider.reject-threshold=55"
})
class InfoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void reportsServiceIdentityAndSplitMockedDependencies() throws Exception {
        mvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value("neo03"))
                .andExpect(jsonPath("$.service").value("Identity Verification (KYC)"))
                .andExpect(jsonPath("$.team").value("Team 03"))
                .andExpect(jsonPath("$.domain").value("kyc"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.orchestratorUrl").value("http://localhost:9000"))
                .andExpect(jsonPath("$.mockedDependencies[0]").value("id-verification-provider"))
                .andExpect(jsonPath("$.mockedDependencies[1]").value("sanctions-list"))
                .andExpect(jsonPath("$.mockedDependencies[2]").value("document-db"));
    }

    @Test
    void reportsTheConfidenceThresholdsSoTheUiNeedNotHardcodeThem() throws Exception {
        // Deliberately NOT the 92/60 defaults: a UI that printed the numbers as literals would
        // pass a test that asserted the defaults and still be wrong on any environment that
        // moved them. Overriding here is what proves the values are actually read.
        mvc.perform(get("/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProvider.acceptThreshold").value(95))
                .andExpect(jsonPath("$.idProvider.rejectThreshold").value(55));
    }
}
