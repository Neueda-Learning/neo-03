// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "VERIFIED", "CLEAR" and "SIGNED" too.
import { TONES, toneMapper } from "./design-system";

export const statusTone = toneMapper({
  VERIFIED: TONES.POSITIVE,
  FAILED: TONES.NEGATIVE,
  REVIEW: TONES.WARNING,
  ACCEPTED: TONES.POSITIVE,
  REJECTED: TONES.NEGATIVE,
  REFERRED: TONES.WARNING,
  // Kept although the skeleton never stores it: a row is written only once the work is done. If
  // you start recording an application before you have decided about it, this is already coloured.
  "in-progress": TONES.INFO,
});

/**
 * The labels the board filters on — distinct operator outcomes rather than raw stored statuses.
 *
 * `in-progress` is not here on purpose: the placeholder writes its row after the work, so no row
 * is ever in that state and a chip for it would always read zero. Add it if you change that.
 */
export const STATUSES = [
  "AUTO APPROVED",
  "AUTO REJECTED",
  "MANUALLY APPROVED",
  "MANUALLY REJECTED",
  "REVIEW",
];

export function statusLabel(status, decisionSource) {
  if (status === "VERIFIED" && decisionSource === "MANUAL") {
    return "MANUALLY APPROVED";
  }
  if (status === "FAILED" && decisionSource === "MANUAL") {
    return "MANUALLY REJECTED";
  }
  if (status === "VERIFIED" && decisionSource === "AUTO") {
    return "AUTO APPROVED";
  }
  if (status === "FAILED" && decisionSource === "AUTO") {
    return "AUTO REJECTED";
  }
  return status;
}

export function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString() : "—";
}

/**
 * What one call to an identity agency came back as.
 *
 * `SHORT_CIRCUITED` is NEUTRAL, not negative, and the distinction is the point: nothing failed
 * on that attempt because nothing was tried. The circuit breaker had already decided the provider
 * was down and skipped it. Colouring it red would report a failure that never happened, and hide
 * the more interesting fact — that the module stopped calling on purpose.
 */
export const attemptTone = toneMapper({
  ANSWERED: TONES.POSITIVE,
  TIMEOUT: TONES.WARNING,
  ERROR: TONES.NEGATIVE,
  REFUSED: TONES.NEGATIVE,
  SHORT_CIRCUITED: TONES.NEUTRAL,
});

/**
 * Which end of the failover chain an agency sits at.
 *
 * Derived here rather than spelled out in a screen, so `NATIONAL` appears in exactly one place in
 * the front end. The backend's own `Agency` enum is ordered so that the first constant is the
 * primary; if a third source is ever added, this is the single line that has to learn about it.
 */
export function agencyRole(agency) {
  if (!agency) return null;
  return agency === "NATIONAL" ? "primary" : "fallback";
}

/**
 * A clock time WITH milliseconds.
 *
 * `time()` above uses `toLocaleTimeString()`, which drops them — fine for "when did this
 * application arrive", useless here. The gaps between consecutive attempts are the retry
 * ladder's backoff, and at second precision a 1.0s wait and a 1.9s wait look identical.
 */
export function clockTime(iso) {
  if (!iso) return "—";
  const at = new Date(iso);
  return `${at.toLocaleTimeString()}.${String(at.getMilliseconds()).padStart(3, "0")}`;
}

/**
 * The wait between two attempts, in seconds.
 *
 * Returns null for the first attempt of a ladder — nothing precedes it — and ALSO for a gap that
 * rounds to zero. The fallback is tried immediately after the primary's budget is spent, with no
 * backoff at all, and the few milliseconds between those two rows are the call returning, not a
 * wait. Rendering "0.0s" there would put a measured-looking number on a decision never taken.
 */
export function gapSeconds(previousIso, iso) {
  if (!previousIso || !iso) return null;
  const gap = (new Date(iso) - new Date(previousIso)) / 1000;
  return gap >= 0.05 ? gap.toFixed(1) : null;
}

/**
 * Which band a confidence score fell into — the module's own three-way decision.
 *
 * ONE function, used for both the colour of the bar and the sentence under it, so the two can
 * never tell different stories about the same number. Returns null when the thresholds have not
 * loaded from /info yet: a band asserted without them would be a guess, and guessing green on a
 * failing score is the worst direction to guess in.
 */
export function confidenceBand(confidence, thresholds) {
  if (confidence == null || !thresholds) return null;
  if (confidence >= thresholds.acceptThreshold) return "accept";
  if (confidence <= thresholds.rejectThreshold) return "reject";
  return "review";
}

/**
 * Green, yellow, red — the reading everyone brings to a score out of a hundred.
 *
 * The middle band is INFO and not WARNING, which looks wrong and is deliberate: under the glass
 * theme `--ds-tone-warning-accent` is #3f7cbe, a BLUE, and the yellow in the palette lives on
 * `--ds-tone-info-accent` (#a8bf1f, volt). Tone names in this system are slots in a palette, not
 * colour names, and the slot holding yellow here is info.
 *
 * The cost is that the REVIEW badge above the bar stays blue, because that maps through
 * `statusTone` to warning. Bar and badge therefore disagree on a borderline case. Aligning them
 * properly means changing what warning IS, which is a theme edit — off limits to an app, and it
 * would repaint every REVIEW in all ten modules.
 */
export const confidenceTone = toneMapper({
  accept: TONES.POSITIVE,
  review: TONES.INFO,
  reject: TONES.NEGATIVE,
});
