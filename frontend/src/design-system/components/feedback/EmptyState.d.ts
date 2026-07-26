import * as React from 'react';

export interface EmptyStateProps extends React.HTMLAttributes<HTMLDivElement> {
  /** State the next action, not the absence: "Search for an applicant to begin". */
  title?: React.ReactNode;
  /** A Button, when there is one obvious thing to do. */
  action?: React.ReactNode;
  /** Drop the dashed border — for an empty state inside a Card. */
  flush?: boolean;
}

export declare function EmptyState(props: EmptyStateProps): JSX.Element;
