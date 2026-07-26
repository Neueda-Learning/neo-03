import React from 'react';
import { cx } from '../../internal/cx.js';

/**
 * The board. Columns are declared, not hand-written as <td>s, so ten teams
 * produce ten tables that look and behave the same.
 *
 * Two platform rules are built in rather than left to each team to remember
 * (v5 DECISIONS.md §3, restated in every module brief):
 *
 *   1. Lists are EMPTY by default — pass `empty` and it is shown whenever there
 *      are no rows, instead of a bare table head over nothing.
 *   2. At most 10 rows. `maxRows` caps what is rendered and, when `total`
 *      exceeds it, the footnote says so and tells the operator to refine.
 *
 * A row can expand in place (the per-application event log) via `renderExpanded`.
 */
export function DataTable({
  columns,
  rows,
  rowKey,
  maxRows = 10,
  total,
  onRowClick,
  selectedKey,
  expandedKey,
  renderExpanded,
  empty,
  footnote,
  rowTone,
  className,
  ...rest
}) {
  const visible = maxRows == null ? rows : rows.slice(0, maxRows);
  const matches = total ?? rows.length;

  if (visible.length === 0) return empty ?? null;

  const capped = matches > visible.length;
  const parts = [
    `${matches} ${matches === 1 ? 'match' : 'matches'}`,
    footnote,
    capped && `showing at most ${visible.length} — refine your search to narrow further`,
  ].filter(Boolean);

  return (
    <div className={className}>
      <div className="ds-table-wrap">
        <div className="ds-table-scroll">
          <table className="ds-table" {...rest}>
            <thead>
              <tr>
                {columns.map((col) => (
                  <th
                    key={col.key}
                    className={cx(col.numeric && 'ds-table__cell--num', col.tight && 'ds-table__cell--tight')}
                    style={col.width ? { width: col.width } : undefined}
                    scope="col"
                  >
                    {col.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visible.map((row, index) => {
                const key = rowKey ? rowKey(row, index) : index;
                const clickable = Boolean(onRowClick);
                return (
                  <React.Fragment key={key}>
                    <tr
                      className={cx(
                        clickable && 'ds-table__row--clickable',
                        selectedKey != null && selectedKey === key && 'ds-table__row--selected',
                        rowTone?.(row) && `ds-table__row--${rowTone(row)}`
                      )}
                      onClick={clickable ? () => onRowClick(row, key) : undefined}
                    >
                      {columns.map((col) => (
                        <td
                          key={col.key}
                          className={cx(
                            col.numeric && 'ds-table__cell--num',
                            col.mono && 'ds-table__cell--mono',
                            col.tight && 'ds-table__cell--tight'
                          )}
                        >
                          {col.render ? col.render(row, index) : row[col.key]}
                        </td>
                      ))}
                    </tr>
                    {renderExpanded && expandedKey === key && (
                      <tr className="ds-table__detail">
                        <td colSpan={columns.length}>{renderExpanded(row)}</td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      {parts.length > 0 && <p className="ds-table__footnote">{parts.join(' · ')}</p>}
    </div>
  );
}
