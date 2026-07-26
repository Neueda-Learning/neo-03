import * as React from 'react';

export interface SectionProps extends React.HTMLAttributes<HTMLElement> {
  /** Rendered as the 11px uppercase label. Sentence content, shouted by the style. */
  title?: React.ReactNode;
  /** A muted note beside the title. */
  aside?: React.ReactNode;
}

export declare function Section(props: SectionProps): JSX.Element;
