import React from 'react';
import { cx } from '../../internal/cx.js';

/** Multi-line. `mono` for anything machine-shaped — a JSON envelope, a changeset. */
export function Textarea({ invalid = false, mono = false, className, ...rest }) {
  return (
    <textarea
      className={cx('ds-textarea', invalid && 'ds-textarea--invalid', mono && 'ds-textarea--mono', className)}
      aria-invalid={invalid || undefined}
      spellCheck={mono ? false : undefined}
      {...rest}
    />
  );
}
