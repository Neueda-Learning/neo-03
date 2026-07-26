import * as React from 'react';
import type { Tone } from '../../tones';

export interface StatusDotProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
  /** Accessible name and hover title. Always pass one — colour alone is not a signal. */
  label?: string;
}

export declare function StatusDot(props: StatusDotProps): JSX.Element;
