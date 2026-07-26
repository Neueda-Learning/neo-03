import * as React from 'react';

export interface TextInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  /** For references, codes and ids. */
  mono?: boolean;
  size?: 'sm' | 'md';
}

export declare function TextInput(props: TextInputProps): JSX.Element;
