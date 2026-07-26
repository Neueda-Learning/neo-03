import * as React from 'react';

export interface SearchInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** Take the free space in a Toolbar row. */
  grow?: boolean;
}

export declare function SearchInput(props: SearchInputProps): JSX.Element;
