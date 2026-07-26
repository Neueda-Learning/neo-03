import * as React from 'react';

export interface SliderProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: React.ReactNode;
  value: number | string;
  /** Unit shown beside the value, e.g. "ms" or "%". */
  suffix?: React.ReactNode;
}

export declare function Slider(props: SliderProps): JSX.Element;
