package com.neobank.module.integrations.idprovider;

import com.neobank.module.integrations.orchestrator.Application;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * <h2>One HTTP call to one identity source.</h2>
 *
 * <p>This class used to roll dice. It returned {@code -1} a quarter of the time to stand in for a
 * network failure, and otherwise picked a confidence band at random — so the same applicant could
 * verify, park and be rejected on three consecutive runs, and no test could assert anything about
 * any of it. It now calls {@code mock-integration}, which is a real service over a real socket that
 * really can be slow, broken or absent.</p>
 *
 * <p><b>Scope: one call.</b> No retrying, no failover, no circuit breaker — those belong to
 * {@link com.neobank.module.service.ProviderGateway}, because they are policy about how hard to try
 * and this is the thing being tried. Keeping them apart is what makes the ladder testable without a
 * socket and this class testable without a clock.</p>
 *
 * <h3>The privacy law this class exists to obey</h3>
 *
 * <p>{@code identityDocument.documentId} is sent to the provider and appears <b>nowhere else</b> —
 * not in a log line, not in an error message, not in the callback. It is the one field that can
 * identify a person outright, and this module's own rule is that it passes through and is not
 * retained. Every log statement below names the agency and the outcome and nothing else;
 * {@code IdVerificationClientTest} captures the logger and fails the build if a document number
 * ever reaches it.</p>
 */
@Component
public class IdVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(IdVerificationClient.class);

    private final RestClient http;
    private final String baseUrl;

    public IdVerificationClient(@Qualifier("idProviderRestClient") RestClient http,
                                @Value("${id-provider.base-url}") String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Ask one agency about one applicant.
     *
     * @throws ProviderUnavailableException classified {@link AttemptResult#TIMEOUT},
     *         {@link AttemptResult#ERROR} or {@link AttemptResult#REFUSED} — never a raw
     *         {@code RestClientException}, because the caller's job is to decide how to react to
     *         each kind and it should not have to unwrap Spring's exception hierarchy to do it
     */
    public ProviderAnswer verify(Agency agency, Application application) {
        String url = baseUrl + "/api/v1/agencies/" + agency.slug() + "/verifications";
        try {
            ProviderAnswer answer = http.post()
                    .uri(url)
                    .body(requestBody(application))
                    .retrieve()
                    .body(ProviderAnswer.class);

            if (answer == null || answer.confidence() == null) {
                // A 200 with nothing usable in it. Treated as a broken provider rather than a
                // zero score, because a missing number must never become a rejection.
                throw new ProviderUnavailableException(AttemptResult.ERROR,
                        agency + " answered without a confidence score");
            }
            log.info("{} answered — confidence {}", agency, answer.confidence());
            return answer;

        } catch (RestClientResponseException e) {
            // The provider answered, badly. 5xx is its problem; 4xx means we sent something it
            // could not use, which is ours — but both leave us without an answer, and neither is
            // the applicant's fault, so both are retryable and end in a parked case, not a
            // rejected one.
            log.warn("{} returned HTTP {}", agency, e.getStatusCode().value());
            throw new ProviderUnavailableException(AttemptResult.ERROR,
                    agency + " returned HTTP " + e.getStatusCode().value(), e);

        } catch (ResourceAccessException e) {
            AttemptResult result = classify(e);
            log.warn("{} did not answer — {}", agency, result);
            throw new ProviderUnavailableException(result,
                    agency + " did not answer (" + result + ")", e);
        }
    }

    /**
     * <h3>Slow and absent are different problems with different owners.</h3>
     *
     * <p>"Refused" sends an operator to check whether the provider is up. If it <em>is</em> up and
     * merely wedged, that is an afternoon lost to the wrong question — so these are told apart
     * here rather than lumped into one "unreachable".</p>
     *
     * <h3>Why this walks the chain, and why it lists four types for one condition</h3>
     *
     * <p>Spring wraps the real I/O failure, so the top-level exception says nothing. Worse, the
     * JDK's HTTP client raises <b>two different chains for the very same read timeout</b>, and
     * which one you get is not deterministic:</p>
     *
     * <pre>
     *   ResourceAccessException -> java.net.http.HttpTimeoutException
     *   ResourceAccessException -> java.io.IOException -> java.util.concurrent.TimeoutException
     * </pre>
     *
     * <p>Both were observed against the same stalling socket, minutes apart. Handling only the
     * first left the second falling through to the default — which reported a hung provider as
     * REFUSED perhaps half the time. A flaky diagnosis is worse than a consistently wrong one:
     * you cannot even learn to distrust it. {@link SocketTimeoutException} is listed too because
     * it is what the older {@code SimpleClientHttpRequestFactory} throws, so swapping the factory
     * back does not silently reclassify every timeout.</p>
     *
     * <p><b>The default is TIMEOUT, not REFUSED</b>, and that is deliberate. Reaching the end of
     * the chain means we do not recognise the failure. "No answer in time" is then merely
     * imprecise — it is true of everything that lands here. "Nothing was listening" would be an
     * assertion we have not earned, and it is the one that misdirects the person reading it.</p>
     */
    private AttemptResult classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return AttemptResult.TIMEOUT;
            }
            // Only this one earns REFUSED: the OS actively rejected the connection, so we know
            // nothing was accepting on that port.
            if (cause instanceof ConnectException) {
                return AttemptResult.REFUSED;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return AttemptResult.TIMEOUT;
    }

    /**
     * The provider's request body.
     *
     * <p>Built by hand rather than by serialising {@link Application}: the provider is a separate
     * system with its own contract, and handing it the bank's whole application form would send it
     * income, employment and consent data it has no business seeing. A provider gets what it needs
     * to do its job — the four identity fields the brief names, and nothing else.</p>
     */
    private Map<String, Object> requestBody(Application application) {
        Application.Applicant applicant = application.applicant();
        Application.IdentityDocument document = application.identityDocument();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", applicant.fullName());
        body.put("dateOfBirth", applicant.dateOfBirth());
        body.put("address", addressOf(applicant));

        Map<String, Object> documentBody = new LinkedHashMap<>();
        documentBody.put("type", document.type());
        documentBody.put("documentId", document.documentId());
        documentBody.put("issuingCountry", document.issuingCountry());
        documentBody.put("expiryDate", document.expiryDate());
        body.put("document", documentBody);
        return body;
    }

    private Map<String, Object> addressOf(Application.Applicant applicant) {
        Application.Address address = applicant.currentAddress();
        if (address == null) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("line1", address.line1());
        body.put("line2", address.line2());
        body.put("city", address.city());
        body.put("postcode", address.postcode());
        body.put("country", address.country());
        return body;
    }
}
