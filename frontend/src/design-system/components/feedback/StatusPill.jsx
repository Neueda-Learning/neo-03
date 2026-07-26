import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * A live condition: /health says UP, the provider mock is DOWN, the feed is STALE.
 *
 * Distinct from Badge on purpose. A Badge states a decision that was made and will
 * not change on its own; a StatusPill states something true right now that may be
 * different when you next look.
 */
export function StatusPill({ tone = 'neutral', className, children, ...rest }) {
  return (
    <span className={cx('ds-status-pill', `ds-status-pill--${toTone(tone)}`, className)} {...rest}>
      <span className="ds-dot" aria-hidden="true" />
      {children}
    </span>
  );
}
