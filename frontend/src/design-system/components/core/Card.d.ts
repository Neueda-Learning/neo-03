import * as React from 'react';
import type { Tone } from '../../tones';

export interface CardProps extends React.HTMLAttributes<HTMLElement> {
  title?: React.ReactNode;
  /** Sits beside the title, muted — a count, a timestamp, a version pin. */
  subtitle?: React.ReactNode;
  /** Pushed to the right of the head: a Badge, or a small Button. */
  headEnd?: React.ReactNode;
  foot?: React.ReactNode;
  /** Draws a tone-coloured left edge. Use for pass/fail rule sections. */
  tone?: Tone;
  /** Sit on the canvas colour instead of the raised surface. */
  flush?: boolean;
  /** Remove body padding — for a Card wrapping a DataTable edge to edge. */
  bodyless?: boolean;
}

export declare function Card(props: CardProps): JSX.Element;
