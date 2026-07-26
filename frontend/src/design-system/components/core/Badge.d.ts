import * as React from 'react';
import type { Tone } from '../../tones';

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  /** Map your domain word onto a tone with `toneMapper`; never pass the word itself. */
  tone?: Tone;
  size?: 'sm' | 'md';
  /** Prefix a tone-coloured dot — useful when badges sit in a dense table. */
  dot?: boolean;
}

export declare function Badge(props: BadgeProps): JSX.Element;
