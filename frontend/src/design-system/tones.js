/**
 * The five tones, and the one rule about them.
 *
 * The design system does not know a single business word. It does not know what
 * PASSED means, or CLEAR, or SIGNED, or REFERRED — and it must not learn, because
 * ten modules speak ten different vocabularies over one shared contract:
 *
 *   verification  PASSED · FAILED · REVIEW
 *   policy        APPROVED · REFERRED · REJECTED
 *   kyc           VERIFIED · FAILED · REVIEW
 *   screening     CLEAR · REVIEW · HIT
 *   credit        APPROVED · REFERRED · DECLINED
 *   agreement     SIGNED · PENDING · DECLINED · EXPIRED
 *   account       OPENED · FAILED
 *   card          ISSUED · FAILED
 *
 * If Badge knew those words, every module would have to edit the design system to
 * add its own. Instead each app owns one map from its vocabulary to these five
 * tones — see `toneMapper` below — and the design system stays domain-free.
 */

/** @type {{POSITIVE:'positive',WARNING:'warning',NEGATIVE:'negative',INFO:'info',NEUTRAL:'neutral'}} */
export const TONES = {
  /** It worked, it passed, it is clear. */
  POSITIVE: 'positive',
  /** A person must look: review, referred, pending, breaching. */
  WARNING: 'warning',
  /** A business no, or a failure: rejected, declined, hit, expired. */
  NEGATIVE: 'negative',
  /** In flight — running now, waiting on a callback. */
  INFO: 'info',
  /** Nothing has happened yet, or the value carries no judgement. */
  NEUTRAL: 'neutral',
};

export const TONE_LIST = Object.values(TONES);

/** Guard so a typo degrades to neutral instead of rendering an unstyled element. */
export function toTone(value) {
  return TONE_LIST.includes(value) ? value : TONES.NEUTRAL;
}

/**
 * Build an app's status → tone map once, use it everywhere.
 *
 *   const tone = toneMapper({
 *     PASSED: TONES.POSITIVE,
 *     FAILED: TONES.NEGATIVE,
 *     REVIEW: TONES.WARNING,
 *   });
 *   <Badge tone={tone(record.outcome)}>{record.outcome}</Badge>
 *
 * Case-insensitive, because the same concept arrives as `in-progress` from a
 * callback and `IN_PROGRESS` from a journey record.
 */
export function toneMapper(map, fallback = TONES.NEUTRAL) {
  const normalised = new Map(
    Object.entries(map).map(([key, tone]) => [normalise(key), toTone(tone)])
  );
  return (value) => normalised.get(normalise(value)) ?? fallback;
}

function normalise(value) {
  return String(value ?? '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]/g, '_');
}
