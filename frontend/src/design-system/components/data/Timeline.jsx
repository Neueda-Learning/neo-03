import React from 'react';
import { cx } from '../../internal/cx.js';
import { toTone } from '../../tones.js';

/**
 * What happened, in order: the journey's event feed, a case's status history, an
 * override trail.
 *
 * Append-only histories are the audit story in this product — every screen that
 * shows one is showing evidence, so nothing here is ever collapsed away silently.
 */
export function Timeline({ items, className, ...rest }) {
  return (
    <ol className={cx('ds-timeline', className)} {...rest}>
      {items.map((item, index) => (
        <li
          key={item.id ?? index}
          className={cx('ds-timeline__item', item.tone && `ds-timeline__item--${toTone(item.tone)}`)}
        >
          <span className="ds-timeline__marker" aria-hidden="true" />
          <div className="ds-timeline__body">
            <div className="ds-timeline__title">{item.title}</div>
            {item.detail != null && <div className="ds-timeline__detail">{item.detail}</div>}
          </div>
          {item.when != null && <time className="ds-timeline__when">{item.when}</time>}
        </li>
      ))}
    </ol>
  );
}
