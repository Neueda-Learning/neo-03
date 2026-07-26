import * as React from 'react';
import type { Tone } from '../../tones';

export interface Column<Row = any> {
  /** Unique per table; also the property read from the row when `render` is absent. */
  key: string;
  header: React.ReactNode;
  /** Full control of the cell — return a `<Badge />`, a `<Tag />`, a formatted date. */
  render?: (row: Row, index: number) => React.ReactNode;
  /** Right-align and use tabular figures. */
  numeric?: boolean;
  /** Monospace — ids, references, codes. */
  mono?: boolean;
  /** Shrink to content and never wrap. */
  tight?: boolean;
  width?: string;
}

export interface DataTableProps<Row = any>
  extends Omit<React.TableHTMLAttributes<HTMLTableElement>, 'rows'> {
  columns: Column<Row>[];
  rows: Row[];
  rowKey?: (row: Row, index: number) => React.Key;
  /** Hard cap on rendered rows. Defaults to 10 — the platform rule. Pass `null` to lift it. */
  maxRows?: number | null;
  /** Total matches BEFORE capping, so the footnote can say "showing at most 10". */
  total?: number;
  onRowClick?: (row: Row, key: React.Key) => void;
  selectedKey?: React.Key;
  /** Which row is expanded; pair with `renderExpanded`. */
  expandedKey?: React.Key;
  renderExpanded?: (row: Row) => React.ReactNode;
  /** Shown instead of the table when there are no rows. Usually an `<EmptyState />`. */
  empty?: React.ReactNode;
  /** Middle clause of the footnote, e.g. "newest first". */
  footnote?: React.ReactNode;
  rowTone?: (row: Row) => Tone | undefined;
}

export declare function DataTable<Row = any>(props: DataTableProps<Row>): JSX.Element;
