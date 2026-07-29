package com.neobank.mockagency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neobank.mockagency.dto.AgencyConfig;
import com.neobank.mockagency.dto.VerificationRequest;
import com.neobank.mockagency.dto.VerificationResponse;
import com.neobank.mockagency.dto.VerificationResponse.Check;
import com.neobank.mockagency.model.Agency;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AgencyServiceTest {

    private static final Instant FIXED = Instant.parse("2026-07-29T10:14:22Z");

    private AgencyService agencies;

    @BeforeEach
    void setUp() {
        agencies = new AgencyService(
                new ConfidenceBook(), Clock.fixed(FIXED, ZoneOffset.UTC), 0, 0, false);
    }

    private static VerificationRequest request(String documentId) {
        return new VerificationRequest(
                "Maria Nowak",
                "1996-04-11",
                new VerificationRequest.Address("12 Ulica Kwiatowa", null, "Warsaw", "00-001", "PL"),
                new VerificationRequest.Document("PASSPORT", documentId, "PL", "2031-02-28"));
    }

    @Test
    @DisplayName("The national agency answers Maria at 92, with all four checks")
    void nationalAnswersWithFourChecks() {
        VerificationResponse response =
                agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567"));

        assertThat(response.confidence()).isEqualTo(92);
        assertThat(response.agency()).isEqualTo(Agency.NATIONAL_IDENTITY_AGENCY);
        assertThat(response.providerRef()).startsWith("nat-");
        assertThat(response.checkedAt()).isEqualTo(FIXED);
        assertThat(response.checks()).extracting(Check::name)
                .containsExactly("documentGenuine", "nameMatched", "dobConsistent", "addressConfirmed");
        assertThat(response.checks()).allMatch(Check::passed);
    }

    @Test
    @DisplayName("The tax agency answers the SAME confidence but cannot speak to the document")
    void taxAnswersSameScoreWithoutDocumentCheck() {
        VerificationResponse national =
                agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567"));
        VerificationResponse tax =
                agencies.verify(Agency.TAX_AGENCY, request("ZS1234567"));

        // Same score on purpose: a failover must not silently change the applicant's outcome.
        assertThat(tax.confidence()).isEqualTo(national.confidence());
        assertThat(tax.providerRef()).startsWith("tax-");
        // But it has never seen the passport, so documentGenuine is simply absent.
        assertThat(tax.checks()).extracting(Check::name)
                .containsExactly("nameMatched", "dobConsistent", "addressConfirmed")
                .doesNotContain("documentGenuine");
    }

    @Test
    @DisplayName("The kill switch refuses, and refuses only the agency it was set on")
    void killSwitchIsPerAgency() {
        agencies.updateConfig(Agency.NATIONAL_IDENTITY_AGENCY, new AgencyConfig(0, 0, true));

        assertThatThrownBy(() ->
                agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567")))
                .isInstanceOf(AgencyUnavailableException.class);

        // This is the whole failover demo: the fallback is still up.
        assertThat(agencies.verify(Agency.TAX_AGENCY, request("ZS1234567")).confidence())
                .isEqualTo(92);
    }

    @Test
    @DisplayName("A 100% failure rate refuses every call")
    void totalFailureRateRefuses() {
        agencies.updateConfig(Agency.NATIONAL_IDENTITY_AGENCY, new AgencyConfig(0, 100, false));

        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() ->
                    agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567")))
                    .isInstanceOf(AgencyUnavailableException.class);
        }
    }

    @Test
    @DisplayName("ZZ0000000 fails on BOTH agencies, with every dial healthy")
    void corpusFailureDocumentFailsEverywhere() {
        for (Agency agency : Agency.values()) {
            assertThatThrownBy(() -> agencies.verify(agency, request("ZZ0000000")))
                    .as("%s", agency)
                    .isInstanceOf(AgencyUnavailableException.class);
        }
    }

    @Test
    @DisplayName("Latency is applied — a slow agency really is slow")
    void latencyIsHonoured() {
        agencies.updateConfig(Agency.NATIONAL_IDENTITY_AGENCY, new AgencyConfig(300, 0, false));

        long start = System.nanoTime();
        agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(300);
    }

    @Test
    @DisplayName("Reset puts both agencies back and zeroes the counters")
    void resetRestoresHealthyAndClearsCounts() {
        agencies.updateConfig(Agency.NATIONAL_IDENTITY_AGENCY, new AgencyConfig(9000, 100, true));
        agencies.verify(Agency.TAX_AGENCY, request("ZS1234567"));
        assertThat(agencies.callCounts().get("tax")).isEqualTo(1);

        agencies.reset();

        assertThat(agencies.configFor(Agency.NATIONAL_IDENTITY_AGENCY))
                .isEqualTo(AgencyConfig.healthy());
        assertThat(agencies.callCounts().values()).allMatch(count -> count == 0);
        // The demo has to be reversible: Maria verifies at 92 again, first attempt.
        assertThat(agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567")).confidence())
                .isEqualTo(92);
    }

    @Test
    @DisplayName("Configured defaults are what reset restores — a stack can boot already broken")
    void defaultsComeFromConfiguration() {
        AgencyService bootedDown = new AgencyService(
                new ConfidenceBook(), Clock.fixed(FIXED, ZoneOffset.UTC), 0, 0, true);

        assertThatThrownBy(() ->
                bootedDown.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567")))
                .isInstanceOf(AgencyUnavailableException.class);

        bootedDown.reset();
        // Reset means "back to the configured starting position", not "back to healthy".
        assertThatThrownBy(() ->
                bootedDown.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567")))
                .isInstanceOf(AgencyUnavailableException.class);
    }

    @Test
    @DisplayName("Refused calls are still counted — the control panel shows attempts, not successes")
    void refusedCallsAreCounted() {
        agencies.updateConfig(Agency.NATIONAL_IDENTITY_AGENCY, new AgencyConfig(0, 0, true));

        for (int i = 0; i < 3; i++) {
            try {
                agencies.verify(Agency.NATIONAL_IDENTITY_AGENCY, request("ZS1234567"));
            } catch (AgencyUnavailableException expected) {
                // counted anyway
            }
        }
        assertThat(agencies.callCounts().get("national")).isEqualTo(3);
    }
}
