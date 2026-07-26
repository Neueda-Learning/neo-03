import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * Ranked horizontal bars — reason codes by frequency, per-service outcome counts.
 *
 * Horizontal because the labels are things like VER_LIMIT_OUTSIDE_PRODUCT_RANGE,
 * which vertical bars cannot hold. Bars scale against the largest value unless a
 * `max` is given; pass one when two charts must be read side by side.
 */
export function BarChart({ data, max, labelWidth, className, style, ...rest }) {
  const ceiling = max ?? Math.max(1, ...data.map((d) => d.value));
  return (
    <div
      className={cx('ds-bars', className)}
      style={{ ...(labelWidth ? { '--ds-bars-label': labelWidth } : {}), ...style }}
      {...rest}
    >
      {data.map((item) => (
        <div className="ds-bars__row" key={item.label}>
          <span className="ds-bars__label" title={String(item.label)}>
            {item.label}
          </span>
          <span className="ds-bars__track">
            <span
              className="ds-bars__fill"
              style={{
                width: `${Math.round((item.value / ceiling) * 100)}%`,
                ...(item.tone
                  ? { '--ds-bar-color': `var(--ds-tone-${toTone(item.tone)}-accent)` }
                  : {}),
              }}
            />
          </span>
          <span className="ds-bars__value">{item.value}</span>
        </div>
      ))}
    </div>
  );
}
