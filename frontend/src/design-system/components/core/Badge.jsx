import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * An outcome that has been decided: PASSED, HIT, SIGNED, DECLINED.
 *
 * Takes a tone, never a business word — see tones.js for why. For a live
 * condition rather than a decision (is the provider up?) use StatusPill.
 */
export function Badge({ tone = 'neutral', size = 'md', dot = false, className, children, ...rest }) {
  return (
    <span
      className={cx('ds-badge', `ds-badge--${toTone(tone)}`, size === 'sm' && 'ds-badge--sm', className)}
      {...rest}
    >
      {dot && <span className={cx('ds-dot', `ds-dot--${toTone(tone)}`)} aria-hidden="true" />}
      {children}
    </span>
  );
}
