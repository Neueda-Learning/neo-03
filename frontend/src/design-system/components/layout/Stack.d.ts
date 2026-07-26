import * as React from 'react';

export interface StackProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Spacing step, 1–10, from the token scale. */
  gap?: number;
  row?: boolean;
}

export declare function Stack(props: StackProps): JSX.Element;
