import type { ReactNode, HTMLAttributes } from 'react';

export interface SideBrandProps extends HTMLAttributes<HTMLDivElement> {
  /** Who owns this app — the sidebar's equivalent of TopNav's `brand`. */
  brand?: ReactNode;
  /** What the app is — the sidebar's equivalent of TopNav's `product`. */
  product?: ReactNode;
  /** A quieter third line: version, domain, environment. */
  meta?: ReactNode;
}

export declare function SideBrand(props: SideBrandProps): JSX.Element;
