package com.neobank.mockagency.dto;

import com.neobank.mockagency.model.AnswerMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * <h2>The misbehaviour dials — one set per agency.</h2>
 *
 * <p><b>Everything seeds to off.</b> Out of the box this service answers every call, immediately,
 * correctly. That is what makes it usable as the default provider: nothing about the happy path
 * depends on luck.</p>
 *
 * <p>Turn a dial and the caller's resilience becomes observable. That is the whole reason the dials
 * exist — a retry ladder nobody can trigger is a retry ladder nobody can trust.</p>
 *
 * <p><b>They are per agency, and that is the feature.</b> Kill the national agency alone and the
 * caller should fail over to the tax agency and still verify; kill both and it should park the
 * application for a human, never reject it. Those are two different demos and one PUT apart.</p>
 *
 * @param latencyMs      artificial delay before answering. Push it past the caller's 2000 ms
 *                       timeout to force the retry ladder without breaking anything.
 *                       (The module brief seeds 250 ms; 0 is used here because a quarter-second on
 *                       every call slows every test for no teaching value. Set it if you want the
 *                       brief's number.)
 * @param failureRatePct percentage of calls answered {@code 503} instead. 100 is a total outage
 *                       that still lets a lucky retry through at 99 — useful for showing that a
 *                       retry sometimes works.
 * @param killSwitch     refuse every call outright. The unambiguous outage.
 */
public record AgencyConfig(

        @NotNull(message = "is required")
        @Min(value = 0, message = "must not be negative")
        Integer latencyMs,

        @NotNull(message = "is required")
        @Min(value = 0, message = "must be between 0 and 100")
        @Max(value = 100, message = "must be between 0 and 100")
        Integer failureRatePct,

        @NotNull(message = "is required")
        Boolean killSwitch,

        /**
         * What the answer SAYS when one arrives — see {@link AnswerMode}. Orthogonal to the three
         * dials above, which decide whether one arrives at all.
         *
         * <p>Optional in a request body: a caller adjusting latency should not have to restate the
         * mode, so an absent value is read as {@code NORMAL} by {@link #mode()}.</p>
         */
        AnswerMode answerMode) {

    /** Answering, promptly, every time, and saying what the document deserves. */
    public static AgencyConfig healthy() {
        return new AgencyConfig(0, 0, false, AnswerMode.NORMAL);
    }

    /** Never null, so no caller has to defend against an omitted mode. */
    public AnswerMode mode() {
        return answerMode == null ? AnswerMode.NORMAL : answerMode;
    }
}
