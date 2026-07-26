import * as React from 'react';

export interface SplitProps extends React.HTMLAttributes<HTMLDivElement> {
  /** The right-hand column. */
  sidebar?: React.ReactNode;
  /** `default` 1.6fr/1fr · `even` 1fr/1fr · `wide-main` 2.2fr/1fr */
  ratio?: 'default' | 'even' | 'wide-main';
}

export declare function Split(props: SplitProps): JSX.Element;
