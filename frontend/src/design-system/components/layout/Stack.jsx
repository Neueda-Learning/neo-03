import React from 'react';
import { cx } from '../../internal/cx.js';

/** Vertical (or horizontal) rhythm from the spacing scale, so screens do not each invent one. */
export function Stack({ gap = 4, row = false, className, style, children, ...rest }) {
  return (
    <div
      className={cx('ds-stack', row && 'ds-stack--row', className)}
      style={{ '--ds-stack-gap': `var(--ds-space-${gap})`, ...style }}
      {...rest}
    >
      {children}
    </div>
  );
}
