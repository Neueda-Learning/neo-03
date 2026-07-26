import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * One number, named. The KPI row above a board.
 *
 * The figure carries the message — a hint may add context but never sells it
 * ("192 in window", not "Great throughput!").
 */
export function MetricTile({ label, value, hint, tone, className, ...rest }) {
  return (
    <div className={cx('ds-metric', tone && `ds-metric--${toTone(tone)}`, className)} {...rest}>
      <div className="ds-metric__label">{label}</div>
      <div className="ds-metric__value">{value}</div>
      {hint != null && <div className="ds-metric__hint">{hint}</div>}
    </div>
  );
}
