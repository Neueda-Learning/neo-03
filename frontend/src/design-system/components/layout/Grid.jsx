import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * Equal columns — the KPI row above a board, the card grid of services.
 * `cols="auto"` fills as many `min`-wide columns as fit.
 */
export function Grid({ cols = 3, min, gap, className, style, children, ...rest }) {
  const auto = cols === 'auto';
  return (
    <div
      className={cx('ds-grid', auto && 'ds-grid--auto', className)}
      style={{
        ...(auto ? {} : { '--ds-grid-cols': cols }),
        ...(min ? { '--ds-grid-min': typeof min === 'number' ? `${min}px` : min } : {}),
        ...(gap ? { gap: `var(--ds-space-${gap})` } : {}),
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  );
}
