import type { ReactNode, HTMLAttributes } from 'react';

export interface SideNavItem {
  id: string;
  label: ReactNode;
  /** A second, quieter line — use it to say what a not-yet-built screen will be. */
  hint?: ReactNode;
  /** Renders a real disabled button: announced as unavailable, not just greyed. */
  disabled?: boolean;
}

export interface SideNavProps extends HTMLAttributes<HTMLElement> {
  items?: Array<SideNavItem | string>;
  /** The active item's id. Caller-owned state, as with TopNav's tabs. */
  active?: string;
  onSelect?: (id: string) => void;
  /** Accessible name for the nav landmark. Defaults to "Screens". */
  label?: string;
}

export declare function SideNav(props: SideNavProps): JSX.Element;
