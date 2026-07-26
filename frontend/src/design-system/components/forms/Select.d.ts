import * as React from 'react';

export type SelectOption = string | { value: string; label: React.ReactNode };

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  options?: SelectOption[];
  /** A disabled first option shown when the value is empty. */
  placeholder?: string;
  invalid?: boolean;
  size?: 'sm' | 'md';
}

export declare function Select(props: SelectProps): JSX.Element;
