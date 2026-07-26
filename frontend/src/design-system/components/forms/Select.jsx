import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A native select — no popup library, no portal, no dependency. Options may be
 * strings or `{ value, label }`.
 */
export function Select({ options = [], placeholder, invalid = false, size = 'md', className, children, ...rest }) {
  return (
    <select
      className={cx('ds-select', invalid && 'ds-select--invalid', size === 'sm' && 'ds-select--sm', className)}
      aria-invalid={invalid || undefined}
      {...rest}
    >
      {placeholder && (
        <option value="" disabled>
          {placeholder}
        </option>
      )}
      {options.map((option) => {
        const value = typeof option === 'string' ? option : option.value;
        const label = typeof option === 'string' ? option : option.label;
        return (
          <option key={value} value={value}>
            {label}
          </option>
        );
      })}
      {children}
    </select>
  );
}
