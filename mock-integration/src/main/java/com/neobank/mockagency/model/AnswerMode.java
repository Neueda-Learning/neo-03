package com.neobank.mockagency.model;

/**
 * What the agency should answer, regardless of who is asking.
 *
 * <p>The dials in {@code AgencyConfig} decide whether an answer arrives at all — slow, refused,
 * dropped. This decides what the answer SAYS when one does. They are orthogonal: a mode of
 * {@code ALL_PASS} with the kill switch on still refuses every call.</p>
 *
 * <p>It exists because a demo needs to reach an outcome on demand. {@code NORMAL} is deterministic
 * per document, which is right for reproducible checkpoints and useless when you want to show a
 * rejection to a room and the document in front of you happens to score 94.</p>
 */
public enum AnswerMode {

    /**
     * The confidence is a function of the document number — the same applicant always scores the
     * same, on every machine and every run. The default, and the only mode a checkpoint should
     * ever be written against.
     */
    NORMAL(null),

    /** Every document verifies. */
    ALL_PASS(96),

    /** Every document lands between the thresholds, so every case parks for a human. */
    ALL_REVIEW(74),

    /** Every document is refused. */
    ALL_FAIL(30);

    private final Integer forcedConfidence;

    AnswerMode(Integer forcedConfidence) {
        this.forcedConfidence = forcedConfidence;
    }

    /**
     * The score this mode forces, or {@code null} to leave it to the document.
     *
     * <p>The numbers are pinned against the SEEDED thresholds (reject 60, accept 92) and sit clear
     * of both boundaries on purpose — 96, 74 and 30 rather than 92, 76 and 60 — so that nudging a
     * threshold by a point or two does not silently turn "all pass" into "all review". If the
     * thresholds are ever moved far, these move with them.</p>
     */
    public Integer forcedConfidence() {
        return forcedConfidence;
    }
}
