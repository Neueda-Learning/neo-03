import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * Main column plus sidebar — the shape of every case-detail screen in the briefs:
 * the record and its rule results on the left, the live applicant panel on the
 * right. Collapses to one column under 900px.
 */
export function Split({ sidebar, ratio = 'default', className, children, ...rest }) {
  return (
    <div
      className={cx('ds-split', ratio !== 'default' && `ds-split--${ratio}`, className)}
      {...rest}
    >
      <div>{children}</div>
      <div>{sidebar}</div>
    </div>
  );
}
