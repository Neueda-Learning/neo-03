import * as React from 'react';

export type KeyValueItem =
  | { label: React.ReactNode; value: React.ReactNode; mono?: boolean }
  | [React.ReactNode, React.ReactNode];

export interface KeyValueProps extends React.HTMLAttributes<HTMLDListElement> {
  items: KeyValueItem[];
  /** Label above value instead of beside it. */
  stacked?: boolean;
  /** CSS width for the label column, e.g. "38%". */
  keyWidth?: string;
}

export declare function KeyValue(props: KeyValueProps): JSX.Element;
