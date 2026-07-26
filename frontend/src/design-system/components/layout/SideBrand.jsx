import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The identity box that sits above {@link SideNav}: who is running this, and what it is.
 *
 * Props mirror {@link TopNav} on purpose — `brand` and `product` mean the same things here as
 * they do there, so a developer who has met one already knows this. In a sidebar layout this
 * box replaces the nav bar's brand lock-up.
 *
 * The system carries no domain vocabulary, so it does not know what a "team" is: pass whatever
 * identifies the owner as `brand` and whatever identifies the app as `product`. `meta` is a
 * third, quieter line for anything that changes at runtime — a version, a domain, an
 * environment.
 */
export function SideBrand({ brand, product, meta, className, ...rest }) {
  return (
    <div className={cx('ds-sidebrand', className)} {...rest}>
      {brand != null && <span className="ds-sidebrand__brand">{brand}</span>}
      {product != null && <span className="ds-sidebrand__product">{product}</span>}
      {meta != null && <span className="ds-sidebrand__meta">{meta}</span>}
    </div>
  );
}
