import * as React from 'react';
import type { Tone } from '../../tones';

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  tone?: Tone;
  title?: React.ReactNode;
  /** Usually a "Try again" Button. */
  action?: React.ReactNode;
}

export declare function Alert(props: AlertProps): JSX.Element;
