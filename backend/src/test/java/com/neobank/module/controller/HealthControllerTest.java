package com.neobank.module.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void reportsUpWhenTheDatabaseProbeSucceeds() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        HealthController controller = new HealthController(dataSource,
                new MockEnvironment()
                        .withProperty("service.id", "neo03")
                        .withProperty("service.name", "Identity Verification (KYC)"));

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("serviceId", "neo03");
        assertThat(response.getBody()).containsEntry("service", "Identity Verification (KYC)");
        assertThat(response.getBody()).containsKey("timestamp");
        assertThat(response.getBody().get("database")).isEqualTo(Map.of("status", "UP"));
    }

    @Test
    void reportsDownWhenTheDatabaseProbeFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new IllegalStateException("db unavailable"));

        HealthController controller = new HealthController(dataSource, new MockEnvironment());

        var response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DOWN");
        assertThat(response.getBody()).containsEntry("serviceId", "neo03");
        assertThat(response.getBody().get("database")).isEqualTo(Map.of("status", "DOWN"));
    }
}
