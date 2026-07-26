import * as React from 'react';

export interface CodeBlockProps extends React.HTMLAttributes<HTMLPreElement> {
  /** A string, or any value to pretty-print as JSON. */
  value?: unknown;
  /** Cap the height and scroll inside. */
  scroll?: boolean;
}

export declare function CodeBlock(props: CodeBlockProps): JSX.Element;
