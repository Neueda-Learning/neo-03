package com.neobank.module.integrations.idprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.module.config.AppConfig;
import com.neobank.module.integrations.orchestrator.Application;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * <h2>The two failures {@code MockRestServiceServer} cannot produce.</h2>
 *
 * <p>Every other test here fakes the transport, so the client's own classification of a
 * <b>socket</b> failure is never exercised — and the mock server can only ever return a status
 * code, never hang and never refuse a connection. That left the entire {@code classify()} path,
 * and the read timeout that {@code idProviderRestClient} exists to carry, running for the first
 * time in production.</p>
 *
 * <p>It found a real bug: a provider that accepts the connection and then stalls was being
 * reported as {@code REFUSED} — "nothing was listening" — when something was listening and simply
 * too slow. Those are opposite diagnoses. An operator chasing "refused" checks whether the
 * provider is up; it is, and they lose an afternoon.</p>
 *
 * <p>Real sockets, on an ephemeral port, with a 300 ms budget: the whole class runs in well under
 * a second.</p>
 */
class IdVerificationClientSocketTest {

    private static final Application APPLICATION = new Application(
            "SIM-01", "WEB", "2026-07-25T09:14:00Z",
            new Application.Applicant("Maria Nowak", "1996-04-11", null, null, "PL", "PL",
                    null, null, null, null, null),
            new Application.IdentityDocument("PASSPORT", "ZS1234567", "PL", "2031-02-28"),
            null, null, null, null, null);

    private ServerSocket server;
    private Thread accepter;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null && !server.isClosed()) {
            server.close();
        }
        if (accepter != null) {
            accepter.interrupt();
        }
    }

    private IdVerificationClient clientFor(int port) {
        // The REAL bean under test, with its real request factory — a plain RestClient.builder()
        // would have no read timeout at all and this test would hang instead of failing.
        RestClient http = new AppConfig().idProviderRestClient(RestClient.builder(), 300);
        return new IdVerificationClient(http, "http://127.0.0.1:" + port);
    }

    @BeforeEach
    void reset() {
        server = null;
        accepter = null;
    }

    @Test
    @DisplayName("A stalled provider is TIMEOUT — every time, not most of the time")
    void aStalledProviderIsClassifiedAsTimeout() throws IOException {
        // REPEATED on purpose. The JDK's HTTP client raises two different cause chains for the
        // same read timeout and picks between them non-deterministically:
        //     ResourceAccessException -> HttpTimeoutException
        //     ResourceAccessException -> IOException -> java.util.concurrent.TimeoutException
        // A single call passes on either chain about half the time, which is exactly how the
        // original misclassification survived a green run. Five is enough to make a one-sided
        // classifier fail essentially always.
        for (int attempt = 1; attempt <= 5; attempt++) {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            accepter = new Thread(() -> {
                try (Socket accepted = server.accept()) {
                    // Accept, read nothing, write nothing, hold it open. What the mock's latency
                    // dial does, and what a wedged provider does in production.
                    Thread.sleep(10_000);
                } catch (IOException | InterruptedException expected) {
                    Thread.currentThread().interrupt();
                }
            });
            accepter.setDaemon(true);
            accepter.start();

            assertThatThrownBy(() -> clientFor(server.getLocalPort()).verify(Agency.NATIONAL, APPLICATION))
                    .as("attempt %d", attempt)
                    .isInstanceOf(ProviderUnavailableException.class)
                    .extracting(e -> ((ProviderUnavailableException) e).result())
                    // NOT REFUSED. Something IS listening; it is just too slow to be useful, and
                    // the two diagnoses send an operator to opposite places.
                    .isEqualTo(AttemptResult.TIMEOUT);

            tearDown();
        }
    }

    @Test
    @DisplayName("Nothing listening at all is REFUSED")
    void anAbsentProviderIsClassifiedAsRefused() throws IOException {
        // Bind then immediately close, so the port is almost certainly free and nothing answers.
        int deadPort;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            deadPort = probe.getLocalPort();
        }

        assertThatThrownBy(() -> clientFor(deadPort).verify(Agency.NATIONAL, APPLICATION))
                .isInstanceOf(ProviderUnavailableException.class)
                .extracting(e -> ((ProviderUnavailableException) e).result())
                .isEqualTo(AttemptResult.REFUSED);
    }

    @Test
    @DisplayName("The read timeout is honoured — an attempt cannot outlast its budget")
    void theReadTimeoutActuallyFires() throws IOException {
        // The reason idProviderRestClient exists as a separate bean. Without a read timeout a
        // stalled provider holds a worker thread until the OS gives up — minutes — and the retry
        // ladder never gets to run at all.
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        accepter = new Thread(() -> {
            try (Socket accepted = server.accept()) {
                Thread.sleep(10_000);
            } catch (IOException | InterruptedException expected) {
                Thread.currentThread().interrupt();
            }
        });
        accepter.setDaemon(true);
        accepter.start();

        IdVerificationClient client = clientFor(server.getLocalPort());
        long start = System.nanoTime();
        assertThatThrownBy(() -> client.verify(Agency.NATIONAL, APPLICATION))
                .isInstanceOf(ProviderUnavailableException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Generous upper bound: the point is that it gives up near its budget rather than waiting
        // out the ten-second stall.
        assertThat(elapsedMs).isLessThan(3_000L);
    }
}
