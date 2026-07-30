package com.neobank.module.integrations.idprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.neobank.module.integrations.orchestrator.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The wire, from this side. {@link MockRestServiceServer} stands in for the agency, so the request
 * this module actually sends and the response it actually parses are both pinned — including the
 * field names, because a renamed field is a silent null rather than a failure.
 */
class IdVerificationClientTest {

    private static final String DOCUMENT_ID = "ZS1234567";

    private static final Application APPLICATION = new Application(
            "SIM-01", "MOBILE_APP", "2026-07-25T09:14:00Z",
            new Application.Applicant("Maria Nowak", "1996-04-11", "maria@example.com",
                    "+48600100200", "PL", "PL", null, "RENTING",
                    new Application.Address("12 Ulica Kwiatowa", null, "Warsaw", "00-001", "PL"),
                    24, 0),
            new Application.IdentityDocument("PASSPORT", DOCUMENT_ID, "PL", "2031-02-28"),
            new Application.Employment("PERMANENT", "Acme", 30),
            new Application.Finances(48000, 1200, 300),
            new Application.Product("CREDIT_CARD_STANDARD", 3000),
            null, null);

    private MockRestServiceServer server;
    private IdVerificationClient client;
    private ListAppender<ILoggingEvent> capturedLogs;
    private ch.qos.logback.classic.Logger clientLogger;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IdVerificationClient(builder.build(), "http://provider:8081");

        capturedLogs = new ListAppender<>();
        capturedLogs.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        capturedLogs.start();
        clientLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(IdVerificationClient.class);
        clientLogger.addAppender(capturedLogs);
        clientLogger.setLevel(Level.TRACE);
    }

    @AfterEach
    void tearDown() {
        clientLogger.detachAppender(capturedLogs);
    }

    @Test
    @DisplayName("Sends the four identity fields to the right agency path, and nothing else")
    void sendsOnlyTheIdentityFields() {
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.fullName").value("Maria Nowak"))
                .andExpect(jsonPath("$.dateOfBirth").value("1996-04-11"))
                .andExpect(jsonPath("$.document.type").value("PASSPORT"))
                .andExpect(jsonPath("$.document.documentId").value(DOCUMENT_ID))
                .andExpect(jsonPath("$.document.issuingCountry").value("PL"))
                .andExpect(jsonPath("$.document.expiryDate").value("2031-02-28"))
                .andExpect(jsonPath("$.address.city").value("Warsaw"))
                // A provider gets what it needs to do its job. Income, employment and the
                // requested credit limit are the bank's business and are not sent.
                .andExpect(jsonPath("$.finances").doesNotExist())
                .andExpect(jsonPath("$.employment").doesNotExist())
                .andExpect(jsonPath("$.product").doesNotExist())
                .andRespond(withSuccess("""
                        {"providerRef":"nat-1","agency":"NATIONAL_IDENTITY_AGENCY",
                         "confidence":92,
                         "checks":[{"name":"documentGenuine","passed":true}]}""",
                        MediaType.APPLICATION_JSON));

        ProviderAnswer answer = client.verify(Agency.NATIONAL, APPLICATION);

        assertThat(answer.confidence()).isEqualTo(92);
        assertThat(answer.providerRef()).isEqualTo("nat-1");
        assertThat(answer.documentReportedForged()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("The fallback goes to the tax path")
    void theFallbackUsesItsOwnPath() {
        server.expect(requestTo("http://provider:8081/api/v1/agencies/tax/verifications"))
                .andRespond(withSuccess("""
                        {"providerRef":"tax-1","agency":"TAX_AGENCY","confidence":95,
                         "checks":[{"name":"nameMatched","passed":true}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.verify(Agency.TAX, APPLICATION).confidence()).isEqualTo(95);
        server.verify();
    }

    @Test
    @DisplayName("A missing documentGenuine check is NOT read as a forgery")
    void anAbsentDocumentCheckIsNotAFailure() {
        // The tax agency never asserts documentGenuine — it has not seen the document. Treating
        // absent as false would make every single failover reject the applicant.
        server.expect(requestTo("http://provider:8081/api/v1/agencies/tax/verifications"))
                .andRespond(withSuccess("""
                        {"providerRef":"tax-1","agency":"TAX_AGENCY","confidence":95,
                         "checks":[{"name":"nameMatched","passed":true}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.verify(Agency.TAX, APPLICATION).documentReportedForged()).isFalse();
    }

    @Test
    @DisplayName("documentGenuine:false IS read as a forgery")
    void anExplicitlyFailedDocumentCheckIsAForgery() {
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withSuccess("""
                        {"providerRef":"nat-1","agency":"NATIONAL_IDENTITY_AGENCY","confidence":99,
                         "checks":[{"name":"documentGenuine","passed":false}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.verify(Agency.NATIONAL, APPLICATION).documentReportedForged()).isTrue();
    }

    @Test
    @DisplayName("An unknown field in the response is ignored, not fatal")
    void unknownResponseFieldsAreIgnored() {
        // The provider is allowed to grow — the brief's own upgrade path adds a `liveness` block.
        // A module that 500s on a field it has not been told about cannot be upgraded around.
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withSuccess("""
                        {"providerRef":"nat-1","agency":"NATIONAL_IDENTITY_AGENCY","confidence":92,
                         "liveness":{"passed":true,"score":88},
                         "checks":[{"name":"documentGenuine","passed":true}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.verify(Agency.NATIONAL, APPLICATION).confidence()).isEqualTo(92);
    }

    @Test
    @DisplayName("A 503 is an ERROR, and is classified rather than thrown raw")
    void aServerErrorIsClassified() {
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.verify(Agency.NATIONAL, APPLICATION))
                .isInstanceOf(ProviderUnavailableException.class)
                .extracting(e -> ((ProviderUnavailableException) e).result())
                .isEqualTo(AttemptResult.ERROR);
    }

    @Test
    @DisplayName("A 500 is an ERROR too")
    void aFiveHundredIsClassified() {
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.verify(Agency.NATIONAL, APPLICATION))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    @DisplayName("A 200 with no confidence is an ERROR, never a score of zero")
    void aResponseWithoutAConfidenceIsUnusable() {
        // Reading a missing number as 0 would silently REJECT an applicant on the strength of a
        // malformed response. Boxed Integer plus this guard is what stops that.
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withSuccess("""
                        {"providerRef":"nat-1","agency":"NATIONAL_IDENTITY_AGENCY"}""",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.verify(Agency.NATIONAL, APPLICATION))
                .isInstanceOf(ProviderUnavailableException.class)
                .extracting(e -> ((ProviderUnavailableException) e).result())
                .isEqualTo(AttemptResult.ERROR);
    }

    @Test
    @DisplayName("THE DOCUMENT NUMBER NEVER REACHES A LOG LINE — on success or on failure")
    void documentIdIsNeverLogged() {
        // The module's own privacy law: identityDocument.documentId is sent to the provider and
        // appears nowhere else. "Provable, not intended" — this is the proof, and it fails the
        // build the moment someone adds a helpful debug line.
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withSuccess("""
                        {"providerRef":"nat-1","agency":"NATIONAL_IDENTITY_AGENCY","confidence":92,
                         "checks":[{"name":"documentGenuine","passed":true}]}""",
                        MediaType.APPLICATION_JSON));
        client.verify(Agency.NATIONAL, APPLICATION);

        server.reset();
        server.expect(requestTo("http://provider:8081/api/v1/agencies/national/verifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        try {
            client.verify(Agency.NATIONAL, APPLICATION);
        } catch (ProviderUnavailableException expected) {
            // The failure path logs too, and is the easier one to leak from.
            assertThat(expected.getMessage()).doesNotContain(DOCUMENT_ID);
        }

        assertThat(capturedLogs.list).isNotEmpty();
        assertThat(capturedLogs.list)
                .as("no log line may contain the document number")
                .noneMatch(event -> event.getFormattedMessage().contains(DOCUMENT_ID));
    }
}
