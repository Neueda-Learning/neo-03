import * as React from 'react';
import type { Tone } from '../../tones';

export interface TimelineItem {
  id?: React.Key;
  /** The headline — often a `<Badge />` plus a few words. */
  title: React.ReactNode;
  detail?: React.ReactNode;
  /** Pre-formatted; the design system does not format dates. */
  when?: React.ReactNode;
  tone?: Tone;
}

export interface TimelineProps extends React.HTMLAttributes<HTMLOListElement> {
  items: TimelineItem[];
}

export declare function Timeline(props: TimelineProps): JSX.Element;
