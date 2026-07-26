import React from 'react';
import { cx } from '../../internal/cx.js';

/** A 1px rule. Inside a panel only — never to separate two panels; the gap does that. */
export function Divider({ vertical = false, className, ...rest }) {
  return (
    <hr
      className={cx('ds-divider', vertical && 'ds-divider--vertical', className)}
      aria-orientation={vertical ? 'vertical' : 'horizontal'}
      {...rest}
    />
  );
}
