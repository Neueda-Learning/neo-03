import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A vertical screen menu for the {@link AppShell} `side` slot.
 *
 * Same contract as {@link TopNav}'s tabs — `items`, `active`, `onSelect`, and the active item is
 * state the caller owns — so switching a two-screen app from a top bar to a sidebar is a change of
 * component, not of wiring. No router, by the same reasoning: these apps are two to five screens.
 *
 * An item may be `{ id, label, hint, disabled }`. **`disabled` is the point of this component
 * existing** — a sidebar is usually added before the screens that will fill it, and a greyed row
 * with a hint says "this is coming" far better than an empty rail does. A disabled item is a real
 * `<button disabled>`, so it is announced as unavailable rather than merely looking it.
 *
 * @example
 * <SideNav
 *   items={[
 *     { id: 'applications', label: 'Applications' },
 *     { id: 'cases', label: 'Cases', hint: 'your screen goes here', disabled: true },
 *   ]}
 *   active={screen}
 *   onSelect={setScreen}
 * />
 */
export function SideNav({ items = [], active, onSelect, label = 'Screens', className, ...rest }) {
  return (
    <nav className={cx('ds-side', className)} aria-label={label} {...rest}>
      {items.map((item) => {
        const id = typeof item === 'string' ? item : item.id;
        const text = typeof item === 'string' ? item : item.label;
        const hint = typeof item === 'string' ? undefined : item.hint;
        const disabled = typeof item === 'string' ? false : Boolean(item.disabled);
        return (
          <button
            key={id}
            type="button"
            className="ds-side__item"
            disabled={disabled}
            aria-current={active === id ? 'page' : undefined}
            onClick={() => onSelect?.(id)}
          >
            <span className="ds-side__label">{text}</span>
            {hint != null && <span className="ds-side__hint">{hint}</span>}
          </button>
        );
      })}
    </nav>
  );
}
