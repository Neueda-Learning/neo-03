import * as React from 'react';

export interface ChipProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  active?: boolean;
  /** Optional trailing count, e.g. "FAILED 12". */
  count?: number;
}

export declare function Chip(props: ChipProps): JSX.Element;

export type ChipOption = string | { value: string; label: React.ReactNode };

export interface ChipGroupProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
  options: ChipOption[];
  /** The selected option's value. */
  value?: string;
  onChange?: (value: string) => void;
  /** Counts keyed by option value. */
  counts?: Record<string, number>;
}

export declare function ChipGroup(props: ChipGroupProps): JSX.Element;
