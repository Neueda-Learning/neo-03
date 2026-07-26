import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A filter you can switch on: All · PASSED · FAILED · REVIEW.
 *
 * Pressed state is `aria-pressed`, so the styling and the accessibility tree can
 * never disagree.
 */
export function Chip({ active = false, count, className, children, ...rest }) {
  return (
    <button type="button" className={cx('ds-chip', className)} aria-pressed={active} {...rest}>
      {children}
      {count != null && <span className="ds-chip__count">{count}</span>}
    </button>
  );
}

/**
 * A set of mutually exclusive filters. Owns the selection so a screen does not
 * re-implement "which one is on" five times.
 */
export function ChipGroup({ options, value, onChange, counts, className, ...rest }) {
  return (
    <div className={cx('ds-chip-group', className)} role="group" {...rest}>
      {options.map((option) => {
        const key = typeof option === 'string' ? option : option.value;
        const label = typeof option === 'string' ? option : option.label;
        return (
          <Chip
            key={key}
            active={value === key}
            count={counts?.[key]}
            onClick={() => onChange?.(key)}
          >
            {label}
          </Chip>
        );
      })}
    </div>
  );
}
