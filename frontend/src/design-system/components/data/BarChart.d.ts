import * as React from 'react';
import type { Tone } from '../../tones';

export interface BarDatum {
  label: React.ReactNode;
  value: number;
  /** Colours this bar. Omit to use the theme's first series colour. */
  tone?: Tone;
}

export interface BarChartProps extends React.HTMLAttributes<HTMLDivElement> {
  data: BarDatum[];
  /** Fix the scale ceiling so two charts can be compared. Defaults to the largest value. */
  max?: number;
  /** CSS width of the label column, e.g. "220px". */
  labelWidth?: string;
}

export declare function BarChart(props: BarChartProps): JSX.Element;
