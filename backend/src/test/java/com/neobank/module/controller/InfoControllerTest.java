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
        "service.mocked-dependencies=id-verification-provider, sanctions-list, , document-db"
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
}
