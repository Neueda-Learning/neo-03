import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The application bar: brand lock-up, screen tabs, and whatever controls belong to
 * the whole app.
 *
 * The brand is set as type in two parts — "NEO · VERIFICATION" is `brand="NEO"`
 * and `product="Verification"`. There is no logo file in this system; the wordmark
 * is the mark.
 *
 * No router by design (the apps are two to five screens); the active tab is state
 * the caller owns.
 */
export function TopNav({ brand, product, tabs = [], active, onSelect, actions, className, ...rest }) {
  return (
    <header className={cx('ds-nav', className)} {...rest}>
      <div className="ds-nav__brand">
        <span>{brand}</span>
        {product != null && (
          <>
            <span aria-hidden="true">·</span>
            <span className="ds-nav__brand-sub">{product}</span>
          </>
        )}
      </div>

      {tabs.length > 0 && (
        <nav className="ds-nav__tabs" aria-label="Screens">
          {tabs.map((tab) => {
            const id = typeof tab === 'string' ? tab : tab.id;
            const label = typeof tab === 'string' ? tab : tab.label;
            return (
              <button
                key={id}
                type="button"
                className="ds-nav__tab"
                aria-current={active === id ? 'page' : undefined}
                onClick={() => onSelect?.(id)}
              >
                {label}
              </button>
            );
          })}
        </nav>
      )}

      {actions != null && <div className="ds-nav__actions">{actions}</div>}
    </header>
  );
}
