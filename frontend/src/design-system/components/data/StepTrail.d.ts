import * as React from 'react';
import type { Tone } from '../../tones';

export interface TrailStep {
  id?: React.Key;
  /** Step name, shown on hover. */
  label?: string;
  /** What it answered, appended to the hover title. */
  status?: string;
  tone?: Tone;
  /** Draw a ring — the step being worked right now. */
  current?: boolean;
}

export interface StepTrailProps extends React.HTMLAttributes<HTMLSpanElement> {
  steps: TrailStep[];
}

export declare function StepTrail(props: StepTrailProps): JSX.Element;
