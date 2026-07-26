import * as React from 'react';
import type { Tone } from '../../tones';

export interface StatusPillProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: Tone;
}

export declare function StatusPill(props: StatusPillProps): JSX.Element;
