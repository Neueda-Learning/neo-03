import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The title block: what this screen is, what state it is in, and the rules it
 * obeys.
 *
 * `lede` is the line beside the title that every board in the briefs carries —
 * "empty until you search · max 10 rows · names fetched live, never stored". It
 * reads as decoration and is not: it is the screen telling the operator, and the
 * auditor, how it behaves.
 */
export function PageHeader({ title, badge, lede, meta, actions, className, ...rest }) {
  return (
    <div className={cx('ds-page-head', className)} {...rest}>
      <div className="ds-page-head__text">
        <div className="ds-page-head__title-row">
          <h1 className="ds-page-head__title">{title}</h1>
          {badge}
          {lede != null && <span className="ds-page-head__lede">{lede}</span>}
        </div>
        {meta != null && <div className="ds-page-head__meta">{meta}</div>}
      </div>
      {actions != null && <div className="ds-page-head__actions">{actions}</div>}
    </div>
  );
}
