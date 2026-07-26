import React from 'react';
import { cx } from '../../internal/cx.js';
import { TextInput } from './TextInput.jsx';

/**
 * The control that makes an empty board show something.
 *
 * Boards in this product start empty on purpose, so search is the primary act on
 * the screen, not a refinement of a list already there — hence its own component
 * with its own affordance.
 */
export function SearchInput({ grow = false, className, style, ...rest }) {
  return (
    <span
      className={cx('ds-search', className)}
      style={{ ...(grow ? { '--ds-search-grow': '1 1 260px' } : {}), ...style }}
    >
      <span className="ds-search__glyph" aria-hidden="true">
        ⌕
      </span>
      <TextInput type="search" {...rest} />
    </span>
  );
}
