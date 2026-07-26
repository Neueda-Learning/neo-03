import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A titled band within a screen: "RULE RESULTS — EVERY CHECK, PASS OR FAIL".
 * The title is the 11px uppercase label, so it never competes with the page title.
 */
export function Section({ title, aside, className, children, ...rest }) {
  return (
    <section className={cx('ds-section', className)} {...rest}>
      {(title != null || aside != null) && (
        <header className="ds-section__head">
          {title != null && <h2 className="ds-section__title">{title}</h2>}
          {aside != null && <span className="ds-caption">{aside}</span>}
        </header>
      )}
      {children}
    </section>
  );
}
