import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The most-used component in this product.
 *
 * Every board in the v5 briefs is EMPTY until someone searches, so "nothing here"
 * is a designed state and not an accident. The rule the briefs set: an empty state
 * always names the next action — "Pick a row to see the evidence trail", not
 * "No data".
 */
export function EmptyState({ title, children, action, flush = false, className, ...rest }) {
  return (
    <div className={cx('ds-empty', flush && 'ds-empty--flush', className)} {...rest}>
      {title != null && <p className="ds-empty__title">{title}</p>}
      {children != null && <div className="ds-empty__body">{children}</div>}
      {action}
    </div>
  );
}
