/** The five tones every theme must define. Apps map their vocabulary onto these. */
export type Tone = 'positive' | 'warning' | 'negative' | 'info' | 'neutral';

export declare const TONES: {
  POSITIVE: 'positive';
  WARNING: 'warning';
  NEGATIVE: 'negative';
  INFO: 'info';
  NEUTRAL: 'neutral';
};

export declare const TONE_LIST: Tone[];

/** Falls back to `neutral` for anything unrecognised. */
export declare function toTone(value: unknown): Tone;

/**
 * Build a status → tone lookup once, reuse it across every screen.
 * Keys are matched case-insensitively with `-` and spaces normalised to `_`.
 */
export declare function toneMapper(
  map: Record<string, Tone>,
  fallback?: Tone
): (value: unknown) => Tone;
