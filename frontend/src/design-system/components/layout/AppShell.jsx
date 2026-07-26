import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The page frame: a centred content column, with either a bar across the top or a menu down the
 * side, and an optional footer. Every screen in the product is an AppShell with something inside it.
 *
 * Pass **`nav`** (usually a `<TopNav />`) or **`side`** (usually a `<SideBrand />` above a
 * `<SideNav />`), not both — they are two answers to the same question, and a screen wearing both
 * gives the reader two places to look for one thing. `side` suits an app that will grow more
 * screens than a row of tabs can hold; `nav` suits two or three.
 */
export function AppShell({ nav, side, footer, wide = false, className, children, ...rest }) {
  return (
    <div
      className={cx('ds-shell', wide && 'ds-shell--wide', side != null && 'ds-shell--sided', className)}
      {...rest}
    >
      {nav}
      {side != null ? (
        <div className="ds-shell__body">
          {/* <aside> is a landmark, so a screen reader can skip the menu rather than walk it
              on every screen change. */}
          <aside className="ds-shell__side">{side}</aside>
          <main className="ds-shell__main">{children}</main>
        </div>
      ) : (
        <main className="ds-shell__main">{children}</main>
      )}
      {footer != null && <footer className="ds-shell__footer">{footer}</footer>}
    </div>
  );
}
