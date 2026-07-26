import React, { useEffect, useRef } from 'react';
import { cx } from '../../internal/cx.js';

/**
 * A decision that interrupts: "Override this outcome?", "Withdraw this offer?".
 *
 * The one component allowed a shadow. Escape closes it, a click on the scrim
 * closes it, and focus moves inside on open — so a confirmation cannot be
 * dismissed by accident or trap a keyboard user.
 *
 * Rendered inline rather than through a portal: the apps have no nested stacking
 * contexts, and a portal would add a mount point every team would have to know
 * about.
 */
export function Modal({ open, title, onClose, footer, wide = false, className, children, ...rest }) {
  const panel = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event) => {
      if (event.key === 'Escape') onClose?.();
    };
    document.addEventListener('keydown', onKey);
    panel.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="ds-scrim" onMouseDown={(event) => event.target === event.currentTarget && onClose?.()}>
      <div
        ref={panel}
        className={cx('ds-modal', wide && 'ds-modal--wide', className)}
        role="dialog"
        aria-modal="true"
        aria-label={typeof title === 'string' ? title : undefined}
        tabIndex={-1}
        {...rest}
      >
        <header className="ds-modal__head">
          {title != null && <h2 className="ds-modal__title">{title}</h2>}
          <button type="button" className="ds-modal__close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </header>
        <div className="ds-modal__body">{children}</div>
        {footer != null && <footer className="ds-modal__foot">{footer}</footer>}
      </div>
    </div>
  );
}
