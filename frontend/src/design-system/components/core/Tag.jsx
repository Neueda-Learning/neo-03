import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A machine token shown verbatim: a reason code, a module name, a product code.
 * Monospaced and never coloured — it is a fact, not a judgement. Reason codes are
 * displayed, never parsed.
 */
export function Tag({ className, children, ...rest }) {
  return (
    <span className={cx('ds-tag', className)} {...rest}>
      {children}
    </span>
  );
}
