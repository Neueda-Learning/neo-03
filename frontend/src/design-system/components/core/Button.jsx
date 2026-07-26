import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The action control. One `primary` per view — it is the thing the operator came
 * to do (Override decision…, Send, New version…). `danger` borrows the negative
 * tone rather than introducing a second accent.
 */
export function Button({
  variant = 'secondary',
  size = 'md',
  block = false,
  busy = false,
  busyLabel,
  leading,
  trailing,
  className,
  children,
  disabled,
  type = 'button',
  ...rest
}) {
  return (
    <button
      type={type}
      className={cx(
        'ds-btn',
        `ds-btn--${variant}`,
        size !== 'md' && `ds-btn--${size}`,
        block && 'ds-btn--block',
        className
      )}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {busy && <span className="ds-spinner" aria-hidden="true" />}
      {!busy && leading}
      {busy && busyLabel ? busyLabel : children}
      {!busy && trailing}
    </button>
  );
}
