import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The small print under a panel that states a rule the screen obeys:
 * "Nothing here is stored by this module — fetched on open via GET /applications/{id}".
 *
 * These captions are load-bearing in this product: they are how a screen tells an
 * auditor what it does. Muted, small, never a colour.
 */
export function Caption({ className, children, ...rest }) {
  return (
    <p className={cx('ds-caption', className)} {...rest}>
      {children}
    </p>
  );
}
