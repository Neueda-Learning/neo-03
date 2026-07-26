import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * The panel everything sits in. A head (title, subtitle, trailing badge or
 * actions), a body, an optional foot.
 *
 * `tone` draws a 3px edge in that tone — the rule-result cards on every case
 * detail screen ("R1 · Age vs product minimum — FAILED") are a toned Card with a
 * Badge in `headEnd`.
 */
export function Card({
  title,
  subtitle,
  headEnd,
  foot,
  tone,
  flush = false,
  bodyless = false,
  className,
  children,
  ...rest
}) {
  const hasHead = title != null || subtitle != null || headEnd != null;
  return (
    <section
      className={cx(
        'ds-card',
        flush && 'ds-card--flush',
        tone && `ds-card--toned ds-card--${toTone(tone)}`,
        className
      )}
      {...rest}
    >
      {hasHead && (
        <header className="ds-card__head">
          {title != null && <h3 className="ds-card__title">{title}</h3>}
          {subtitle != null && <span className="ds-card__subtitle">{subtitle}</span>}
          {headEnd != null && <div className="ds-card__head-end">{headEnd}</div>}
        </header>
      )}
      <div className={cx('ds-card__body', bodyless && 'ds-card__body--tight')}>{children}</div>
      {foot != null && <footer className="ds-card__foot">{foot}</footer>}
    </section>
  );
}
