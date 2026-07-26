import React, { useId } from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A dial on a mock control panel: latency, failure rate, retry budget.
 *
 * The integration modules exist to teach a failure mode, and this is how an
 * operator makes the dependency misbehave on demand during a demo. Always shows
 * its current value — a slider whose number you cannot read is a toy.
 */
export function Slider({ label, value, suffix, className, id: providedId, ...rest }) {
  const generated = useId();
  const id = providedId ?? generated;
  return (
    <div className={cx('ds-slider', className)}>
      <div className="ds-slider__head">
        <label className="ds-field__label" htmlFor={id}>
          {label}
        </label>
        <span className="ds-slider__value">
          {value}
          {suffix ? <span className="ds-muted"> {suffix}</span> : null}
        </span>
      </div>
      <input id={id} type="range" value={value} {...rest} />
    </div>
  );
}
