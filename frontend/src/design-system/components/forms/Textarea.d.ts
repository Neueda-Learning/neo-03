import * as React from 'react';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
  /** Monospace, and spellcheck off. */
  mono?: boolean;
}

export declare function Textarea(props: TextareaProps): JSX.Element;
