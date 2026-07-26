import * as React from 'react';

export interface FormGridProps extends React.HTMLAttributes<HTMLDivElement> {
  cols?: number;
}

export declare const FormGrid: {
  (props: FormGridProps): JSX.Element;
  /** A child that spans every column. */
  Full: (props: React.HTMLAttributes<HTMLDivElement>) => JSX.Element;
};

export declare function FormActions(props: React.HTMLAttributes<HTMLDivElement>): JSX.Element;
