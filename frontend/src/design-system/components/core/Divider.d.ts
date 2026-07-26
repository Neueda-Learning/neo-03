import * as React from 'react';

export interface DividerProps extends React.HTMLAttributes<HTMLHRElement> {
  vertical?: boolean;
}

export declare function Divider(props: DividerProps): JSX.Element;
