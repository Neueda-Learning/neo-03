import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * The same signal as StatusPill without the word — for dense rows.
 *
 * Colour is never the only channel: pass `label` and it becomes the accessible
 * name and the hover title.
 */
export function StatusDot({ tone = 'neutral', label, className, ...rest }) {
  return (
    <span
      className={cx('ds-dot', `ds-dot--${toTone(tone)}`, className)}
      role={label ? 'img' : undefined}
      aria-label={label}
      title={label}
      {...rest}
    />
  );
}
