import * as React from 'react';
import type { Tone } from '../../tones';

export interface MetricTileProps extends React.HTMLAttributes<HTMLDivElement> {
  label: React.ReactNode;
  value: React.ReactNode;
  hint?: React.ReactNode;
  /** Colours the figure only. Leave unset for a count that carries no judgement. */
  tone?: Tone;
}

export declare function MetricTile(props: MetricTileProps): JSX.Element;
