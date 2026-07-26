import * as React from 'react';

export type NavTab = string | { id: string; label: React.ReactNode };

export interface TopNavProps extends React.HTMLAttributes<HTMLElement> {
  /** The bank: "NEO". */
  brand: React.ReactNode;
  /** This module: "Verification". Rendered after a middle dot, in muted ink. */
  product?: React.ReactNode;
  tabs?: NavTab[];
  /** The active tab's id. */
  active?: string;
  onSelect?: (id: string) => void;
  /** Right-hand controls — a StatusPill, a refresh Button, a toggle. */
  actions?: React.ReactNode;
}

export declare function TopNav(props: TopNavProps): JSX.Element;
