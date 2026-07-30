package com.neobank.module.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Infrastructure beans: a clock, and the two HTTP clients this module talks through.
 *
 * <p>The thread pool the decision runs on is Spring Boot's own
 * {@code applicationTaskExecutor} — no bean needed here. Size and naming are properties:
 * {@code spring.task.execution.*} in {@code application.yml}.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * For calling the orchestrator back. No timeout configured, deliberately: reporting an outcome
     * is the last thing this module does and there is nothing useful to do if it is slow.
     */
    @Bean
    @Primary
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    /**
     * <h3>For calling the identity provider — and it has a deadline.</h3>
     *
     * <p>A SEPARATE client, not a shared one, and that is the whole point of this bean existing. An
     * attempt that may take 2000 ms is what makes the retry ladder meaningful: without a read
     * timeout, a provider that accepts the connection and then hangs holds a worker thread until
     * the OS gives up — minutes — and the ladder never gets to run. But putting that 2 s budget on
     * the shared client would also arm it on the orchestrator callback, where a slow orchestrator
     * would then look like a failure and the module's decision would be lost.</p>
     *
     * <p>Two different conversations, two different deadlines. {@code @Primary} above keeps every
     * existing injection point on the untimed client, so this one is opt-in by qualifier.</p>
     *
     * <p>{@link JdkClientHttpRequestFactory} rather than the default, because it honours a read
     * timeout on the response body as well as the headers — the older factory's
     * {@code setReadTimeout} does not cover a provider that sends headers promptly and then stalls
     * mid-body, which is exactly the shape a latency dial produces.</p>
     */
    @Bean
    public RestClient idProviderRestClient(RestClient.Builder builder,
                                           @Value("${id-provider.timeout-ms:2000}") long timeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return builder.clone().requestFactory(factory).build();
    }
}
