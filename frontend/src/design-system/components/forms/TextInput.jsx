import React from 'react';
import { cx } from '../../internal/cx.js';

/** A single-line control. Wrap in `<Field>` for a label, hint and error. */
export function TextInput({ invalid = false, mono = false, size = 'md', className, ...rest }) {
  return (
    <input
      className={cx(
        'ds-input',
        invalid && 'ds-input--invalid',
        mono && 'ds-input--mono',
        size === 'sm' && 'ds-input--sm',
        className
      )}
      aria-invalid={invalid || undefined}
      {...rest}
    />
  );
}
