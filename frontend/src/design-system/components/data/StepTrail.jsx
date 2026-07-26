import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * The saga as a row of dots — one per step, toned by what that step answered,
 * with a ring on the step running now. Compact enough to live in a table cell, so
 * a board can show a whole journey per row.
 *
 * Each dot carries a title, because colour alone is never the only signal.
 */
export function StepTrail({ steps, className, ...rest }) {
  return (
    <span className={cx('ds-steps', className)} {...rest}>
      {steps.map((step, index) => (
        <span
          key={step.id ?? index}
          className={cx(
            'ds-steps__dot',
            `ds-steps__dot--${toTone(step.tone)}`,
            step.current && 'ds-steps__dot--current'
          )}
          title={step.label ? `${step.label}${step.status ? ` — ${step.status}` : ''}` : undefined}
        />
      ))}
    </span>
  );
}
