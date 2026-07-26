import * as React from 'react';

export interface AppShellProps extends React.HTMLAttributes<HTMLDivElement> {
  /** A bar across the top — usually a `<TopNav />`. Mutually exclusive with `side`. */
  nav?: React.ReactNode;
  /**
   * A menu down the side — usually a `<SideBrand />` above a `<SideNav />`.
   * Mutually exclusive with `nav`: a screen with both has two places to look for one thing.
   */
  side?: React.ReactNode;
  footer?: React.ReactNode;
  /** Let the content column run to the viewport edge — for wide operator boards. */
  wide?: boolean;
}

export declare function AppShell(props: AppShellProps): JSX.Element;
