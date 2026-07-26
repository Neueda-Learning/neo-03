import React, { useId } from 'react';
import { cx } from '../../internal/cx.js';

/**
 * Label, control, hint, error — in that order, every time.
 *
 * Wires the label, the hint and the error to the control by id, so a screen
 * reader reads "Requested credit limit, must be between 500 and 10,000, invalid
 * entry" rather than three unrelated strings. Children may be a node or a render
 * function receiving `{ id, invalid, describedBy }`.
 */
export function Field({ label, hint, error, required = false, htmlFor, className, children, ...rest }) {
  const generated = useId();
  const id = htmlFor ?? generated;
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  // The label only claims an id it can be sure reached a control: either the
  // render-prop form handed it over, or the caller passed `htmlFor` itself. A
  // plain node child gets a label with no `for` rather than one pointing at
  // nothing — a dangling `for` is worse than none, because it looks correct in a
  // diff and does nothing for a mouse or a screen reader.
  const wired = typeof children === 'function' || htmlFor != null;

  return (
    <div className={cx('ds-field', className)} {...rest}>
      {label != null && (
        <label className="ds-field__label" htmlFor={wired ? id : undefined}>
          {label}
          {required && (
            <span className="ds-field__required" aria-hidden="true">
              *
            </span>
          )}
        </label>
      )}
      {typeof children === 'function'
        ? children({ id, invalid: Boolean(error), describedBy })
        : children}
      {hint && (
        <span className="ds-field__hint" id={hintId}>
          {hint}
        </span>
      )}
      {error && (
        <span className="ds-field__error" id={errorId}>
          {error}
        </span>
      )}
    </div>
  );
}
