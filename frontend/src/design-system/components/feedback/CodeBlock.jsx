import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A payload shown verbatim: the envelope that was sent, the callback that came
 * back, a config diff.
 *
 * Pass an object as `value` and it is pretty-printed — the alternative is every
 * screen writing `JSON.stringify(x, null, 2)` and one of them forgetting the
 * indent.
 */
export function CodeBlock({ value, scroll = false, className, children, ...rest }) {
  const text =
    children != null ? children : typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return (
    <pre className={cx('ds-code', scroll && 'ds-code--scroll', className)} {...rest}>
      {text}
    </pre>
  );
}
