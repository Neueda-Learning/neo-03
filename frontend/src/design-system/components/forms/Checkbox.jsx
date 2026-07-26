import React from 'react';
import { cx } from '../../internal/cx.js';

/** A checkbox and its label as one clickable target. */
export function Checkbox({ label, disabled = false, className, ...rest }) {
  return (
    <label className={cx('ds-check', disabled && 'ds-check--disabled', className)}>
      <input type="checkbox" disabled={disabled} {...rest} />
      <span>{label}</span>
    </label>
  );
}
