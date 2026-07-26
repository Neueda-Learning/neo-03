import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * Something the operator must read: an unreachable API, a stale build, a rule
 * that will change the next decision.
 *
 * `negative` is for a real fault. A business "no" is not a fault — a rejected
 * application is a Badge on a record, never an Alert.
 */
export function Alert({ tone = 'negative', title, action, className, children, ...rest }) {
  return (
    <div className={cx('ds-alert', `ds-alert--${toTone(tone)}`, className)} role="status" {...rest}>
      <div className="ds-alert__body">
        {title != null && <div className="ds-alert__title">{title}</div>}
        {children}
      </div>
      {action}
    </div>
  );
}
