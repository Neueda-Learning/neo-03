// This module's vocabulary, mapped onto the design system's five tones — once, here, so no
// screen ever guesses what colour a status is.
//
// The design system deliberately knows no business words (design-system/DESIGN.md § "Tones"):
// ten modules speak ten vocabularies over one contract, and a Badge that knew "ACCEPTED" would
// have to learn "VERIFIED", "CLEAR" and "SIGNED" too.
import { TONES, toneMapper } from './design-system';

export const statusTone = toneMapper({
  ACCEPTED: TONES.POSITIVE,
  REJECTED: TONES.NEGATIVE,
  REFERRED: TONES.WARNING,
  // Kept although the skeleton never stores it: a row is written only once the work is done. If
  // you start recording an application before you have decided about it, this is already coloured.
  'in-progress': TONES.INFO,
});

/**
 * The statuses the board filters on — the three a module can answer with.
 *
 * `in-progress` is not here on purpose: the placeholder writes its row after the work, so no row
 * is ever in that state and a chip for it would always read zero. Add it if you change that.
 */
export const STATUSES = ['ACCEPTED', 'REJECTED', 'REFERRED'];

export function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString() : '—';
}
