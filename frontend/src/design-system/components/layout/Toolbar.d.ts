import * as React from 'react';

export interface ToolbarProps extends React.HTMLAttributes<HTMLDivElement> {}

export declare const Toolbar: {
  (props: ToolbarProps): JSX.Element;
  Group: (props: React.HTMLAttributes<HTMLDivElement>) => JSX.Element;
  Spacer: () => JSX.Element;
};
