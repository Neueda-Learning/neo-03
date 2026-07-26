import React from 'react';
import { cx } from '../../internal/cx.js';

/** The row of controls above a board: search, filter chips, a date range, actions. */
export function Toolbar({ className, children, ...rest }) {
  return (
    <div className={cx('ds-toolbar', className)} {...rest}>
      {children}
    </div>
  );
}

/** Keeps a set of controls together when the toolbar wraps. */
Toolbar.Group = function ToolbarGroup({ className, children, ...rest }) {
  return (
    <div className={cx('ds-toolbar__group', className)} {...rest}>
      {children}
    </div>
  );
};

/** Pushes everything after it to the right. */
Toolbar.Spacer = function ToolbarSpacer() {
  return <span className="ds-toolbar__spacer" />;
};
