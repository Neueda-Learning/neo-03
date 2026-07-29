package com.neobank.mockagency.service;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * <h2>What score a document gets — and why it is never random.</h2>
 *
 * <p>The thing this service replaced rolled dice: {@code random.nextInt(3)} picked a confidence
 * band on every call, so the same applicant could verify, park for review and get rejected on three
 * consecutive runs. That is not a mock of a provider, it is a coin. You cannot write a checkpoint
 * against it, you cannot demonstrate anything twice, and a failing test tells you nothing.</p>
 *
 * <p><b>Here a score is a pure function of the document id.</b> Same document, same answer, every
 * run, on every machine. That is what makes "Maria verifies at 92 in one attempt" a fact you can
 * put in a test and read off a screen.</p>
 *
 * <h3>Pinned fixtures</h3>
 *
 * <p>Four document ids are fixed by hand because a spec checkpoint or the sidecar's scenario corpus
 * names them. Everything else is derived (below). The pins are what let the four worked examples in
 * the module brief reproduce.</p>
 *
 * <h3>Everything else</h3>
 *
 * <p>{@code 61 + floorMod(documentId.hashCode(), 40)} → 61..100. {@code String.hashCode} is
 * specified by the Java Language Specification, so it is stable across JVMs, machines and runs —
 * this is deliberately NOT {@code Object.hashCode} or a hash of anything mutable. The range starts
 * at 61 so an unpinned applicant lands in the REVIEW band at worst and the journey stays green by
 * default; the interesting outcomes are the ones you asked for by name.</p>
 */
@Service
public class ConfidenceBook {

    /**
     * The corpus convention, from the sidecar's scenario library (SIM-14): <i>"a documentId of
     * ZZ0000000 means 'make your mock provider fail'. Wire your mock to time out or 503 on it."</i>
     *
     * <p>It fails on BOTH agencies, so it exercises the whole ladder — three primary attempts, then
     * the one fallback attempt — and ends where an outage should end: parked for a human, never
     * rejected. An outage says nothing about the applicant.</p>
     */
    public static final String ALWAYS_FAILS = "ZZ0000000";

    private static final int FLOOR = 61;
    private static final int SPREAD = 40;

    /**
     * Hand-pinned scores. Each one exists to make a specific checkpoint reproducible — do not
     * change a value without changing the checkpoint that reads it.
     */
    private static final Map<String, Integer> PINNED = Map.of(
            // SIM-01 · Maria Nowak. EXACTLY the accept threshold, which is the point: the brief
            // asks "your accept threshold is 92 and her confidence is 92 — pass or park?" and the
            // answer is pass. A 93 here would let a >-vs->= bug through unnoticed.
            "ZS1234567", 92,

            // SIM-05 · Priya Raman. The spec's "a document the provider scores 74 → REVIEW"
            // worked example. No corpus scenario produced 74, so this is the mock choosing one
            // document to be the review case and writing it down.
            "IN5540982", 74,

            // SIM-11 · Rafael Santos. Below the reject threshold, so the FAILED band is reachable
            // end to end. Without a pin here the whole reject path is untestable through the
            // stack, because every derived score is 61 or above.
            "BR6640281", 41,

            // SIM-02 · Jonas Meyer, the corpus's only DRIVING_LICENCE. Pinned high so a
            // non-passport document has a known-good answer.
            "MEYER701794JM9AB", 95
    );

    /**
     * The confidence this document deserves, 0-100.
     *
     * @param documentId the applicant's identity-document number. <b>Never logged here or
     *                   anywhere else</b> — see the caller's privacy note.
     */
    public int confidenceFor(String documentId) {
        Integer pinned = PINNED.get(documentId);
        if (pinned != null) {
            return pinned;
        }
        return FLOOR + Math.floorMod(documentId.hashCode(), SPREAD);
    }

    /**
     * Whether this document should be reported as genuine.
     *
     * <p>Tied to the score rather than pinned separately: a document the register scores in the
     * reject band is one it does not believe in. This is what makes the caller's
     * {@code KYC_DOCUMENT_INVALID} path reachable, and it is checked BEFORE the confidence bands
     * there — a forgery is a forgery whatever number sits beside it.</p>
     */
    public boolean genuineFor(String documentId) {
        return confidenceFor(documentId) > 60;
    }

    /** Whether this document is the corpus's "make the provider fail" fixture. */
    public boolean alwaysFails(String documentId) {
        return ALWAYS_FAILS.equals(documentId);
    }
}
