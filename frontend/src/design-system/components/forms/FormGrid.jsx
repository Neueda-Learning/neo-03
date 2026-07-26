import React from 'react';
import { cx } from '../../internal/cx.js';

/** Fields in columns, collapsing to one under 720px. `<FormGrid.Full>` spans the row. */
export function FormGrid({ cols = 2, className, style, children, ...rest }) {
  return (
    <div className={cx('ds-form-grid', className)} style={{ '--ds-form-cols': cols, ...style }} {...rest}>
      {children}
    </div>
  );
}

FormGrid.Full = function FormGridFull({ className, children, ...rest }) {
  return (
    <div className={cx('ds-form-grid__full', className)} {...rest}>
      {children}
    </div>
  );
};

/** The row of buttons that closes a form. Primary action first. */
export function FormActions({ className, children, ...rest }) {
  return (
    <div className={cx('ds-form-actions', className)} {...rest}>
      {children}
    </div>
  );
}
