import * as React from 'react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** `primary` fills with the accent — at most one per view. `danger` uses the negative tone. */
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  /** Fill the width of the container. */
  block?: boolean;
  /** Shows a spinner and disables the button. */
  busy?: boolean;
  /** Label to show while `busy`, e.g. "Sending…". Falls back to `children`. */
  busyLabel?: React.ReactNode;
  leading?: React.ReactNode;
  /** Trailing glyph. Plain characters — the system ships no icon set. */
  trailing?: React.ReactNode;
}

export declare function Button(props: ButtonProps): JSX.Element;
