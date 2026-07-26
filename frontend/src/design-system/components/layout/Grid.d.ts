import * as React from 'react';

export interface GridProps extends React.HTMLAttributes<HTMLDivElement> {
  /** A fixed column count, or `'auto'` to fill by `min` width. */
  cols?: number | 'auto';
  /** Minimum column width when `cols="auto"`. Number is treated as px. */
  min?: number | string;
  /** Spacing step, 1–10, from the token scale. */
  gap?: number;
}

export declare function Grid(props: GridProps): JSX.Element;
