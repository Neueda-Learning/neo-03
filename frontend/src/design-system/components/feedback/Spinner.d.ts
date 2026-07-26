import * as React from 'react';

export interface SpinnerProps extends React.HTMLAttributes<HTMLSpanElement> {
  size?: 'md' | 'lg';
  label?: string;
}

export declare function Spinner(props: SpinnerProps): JSX.Element;
