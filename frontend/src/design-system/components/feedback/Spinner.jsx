import React from 'react';
import { cx } from '../../internal/cx.js';

/** Work is in flight. The only thing in the system that moves. */
export function Spinner({ size = 'md', label = 'Loading', className, ...rest }) {
  return (
    <span
      className={cx('ds-spinner', size === 'lg' && 'ds-spinner--lg', className)}
      role="status"
      aria-label={label}
      {...rest}
    />
  );
}
