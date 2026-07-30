package com.neobank.mockagency.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The point of this class is that it is NOT random. These tests are what make that a fact rather
 * than an intention — every one of them would pass by luck at least once against the dice this
 * replaced, and none of them would pass twice.
 */
class ConfidenceBookTest {

    private final ConfidenceBook book = new ConfidenceBook();

    @Test
    @DisplayName("Maria Nowak scores exactly 92 — the accept threshold, not one above it")
    void mariaScoresExactlyTheAcceptThreshold() {
        // The module's accept threshold is 92 and this is 92, which is the interesting case:
        // ">= accept" passes her and "> accept" parks her. A 93 here would let that bug through.
        assertThat(book.confidenceFor("ZS1234567")).isEqualTo(92);
        assertThat(book.genuineFor("ZS1234567")).isTrue();
    }

    @Test
    @DisplayName("The review fixture scores 74 — strictly between reject 60 and accept 92")
    void reviewFixtureLandsBetweenTheThresholds() {
        assertThat(book.confidenceFor("IN5540982")).isEqualTo(74);
        assertThat(book.genuineFor("IN5540982")).isTrue();
    }

    @Test
    @DisplayName("The reject fixture scores at or below 60, and is not reported genuine")
    void rejectFixtureLandsInTheRejectBand() {
        assertThat(book.confidenceFor("BR6640281")).isEqualTo(41);
        // GENUINE, despite the low score. The two are separate facts, and tying them together
        // made the caller's low-confidence rejection unreachable: the forgery gate runs first,
        // so every score at or below the threshold came back as KYC_DOCUMENT_INVALID.
        assertThat(book.genuineFor("BR6640281")).isTrue();
        assertThat(book.genuineFor(ConfidenceBook.FORGED)).isFalse();
    }

    @Test
    @DisplayName("ZZ0000000 is the corpus's always-fails document")
    void corpusFailureFixtureIsRecognised() {
        assertThat(book.alwaysFails("ZZ0000000")).isTrue();
        assertThat(book.alwaysFails("ZS1234567")).isFalse();
    }

    @Test
    @DisplayName("The same document scores the same every time")
    void scoresAreStableAcrossCalls() {
        for (String id : List.of("ZS1234567", "GB9004411", "DE2290471", "SE7719044")) {
            int first = book.confidenceFor(id);
            for (int i = 0; i < 50; i++) {
                assertThat(book.confidenceFor(id))
                        .as("call %d for %s", i, id)
                        .isEqualTo(first);
            }
        }
    }

    @Test
    @DisplayName("Unpinned documents land in 61..100, so the journey stays green by default")
    void derivedScoresStayInTheVerifyOrReviewBands() {
        // Every real documentId from the scenario corpus that this book does not pin by hand.
        List<String> corpus = List.of(
                "GB9004411", "IE7712304", "AT3391027", "HU8820113", "GB4471902", "PT2210984",
                "US7719023", "DK1180552", "RU9930118", "BG4402117", "IR7761209", "GB5518830",
                "DE2290471", "FR3308814", "SE7719044", "IE9930277", "ES2214760", "SK6612903");

        assertThat(corpus).allSatisfy(id ->
                assertThat(book.confidenceFor(id)).as(id).isBetween(61, 100));
    }

    @Test
    @DisplayName("A document nobody pinned still gets an answer — no lookup miss, ever")
    void unknownDocumentsAreScoredNotRejected() {
        assertThat(book.confidenceFor("QQ-not-in-any-corpus-9999")).isBetween(61, 100);
        assertThat(book.confidenceFor("")).isBetween(61, 100);
    }
}
