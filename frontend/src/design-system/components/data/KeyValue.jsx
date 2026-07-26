import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A record read as facts: the applicant sidebar, the workings behind a decision,
 * the pinned config a case was decided under.
 *
 * `stacked` puts the label above the value — use it in a narrow column.
 */
export function KeyValue({ items, stacked = false, keyWidth, className, style, ...rest }) {
  return (
    <dl
      className={cx('ds-kv', stacked && 'ds-kv--stacked', className)}
      style={{ ...(keyWidth ? { '--ds-kv-key': keyWidth } : {}), ...style }}
      {...rest}
    >
      {items.map((item, index) => {
        const { label, value, mono } = normalise(item);
        return (
          <React.Fragment key={index}>
            <dt className="ds-kv__key">{label}</dt>
            <dd className={cx('ds-kv__value', mono && 'ds-kv__value--mono')}>{value}</dd>
          </React.Fragment>
        );
      })}
    </dl>
  );
}

function normalise(item) {
  return Array.isArray(item) ? { label: item[0], value: item[1] } : item;
}
