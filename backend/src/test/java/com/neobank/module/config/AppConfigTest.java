package com.neobank.module.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AppConfigTest {

    private final AppConfig appConfig = new AppConfig();

    @Test
    void createsAClockInUtc() {
        assertThat(appConfig.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void buildsTheRestClientFromTheProvidedBuilder() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);

        when(builder.build()).thenReturn(restClient);

        assertThat(appConfig.restClient(builder)).isSameAs(restClient);
        verify(builder).build();
    }
}
