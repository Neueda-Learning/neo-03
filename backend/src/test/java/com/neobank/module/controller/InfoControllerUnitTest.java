package com.neobank.module.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InfoControllerUnitTest {

    @Test
    void returnsAnEmptyMockedDependenciesListWhenThePropertyIsBlank() {
        InfoController controller = new InfoController();
        ReflectionTestUtils.setField(controller, "serviceId", "neo03");
        ReflectionTestUtils.setField(controller, "serviceName", "Identity Verification (KYC)");
        ReflectionTestUtils.setField(controller, "team", "TEAM 03");
        ReflectionTestUtils.setField(controller, "domain", "kyc");
        ReflectionTestUtils.setField(controller, "version", "0.1.0-SNAPSHOT");
        ReflectionTestUtils.setField(controller, "orchestratorUrl", "http://localhost:9000");
        ReflectionTestUtils.setField(controller, "mockedDependencies", "   ");

        Map<String, Object> body = controller.info();

        assertThat(body)
                .containsEntry("serviceId", "neo03")
                .containsEntry("service", "Identity Verification (KYC)")
                .containsEntry("team", "TEAM 03")
                .containsEntry("domain", "kyc")
                .containsEntry("version", "0.1.0-SNAPSHOT")
                .containsEntry("orchestratorUrl", "http://localhost:9000");
        assertThat(body.get("mockedDependencies")).isEqualTo(List.of());
    }
}
