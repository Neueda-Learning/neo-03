import * as React from 'react';

export interface ModalProps extends React.HTMLAttributes<HTMLDivElement> {
  open: boolean;
  title?: React.ReactNode;
  /** Called on Escape, on the close button, and on a scrim click. */
  onClose?: () => void;
  /** The action row. Confirm is usually disabled until the form is complete. */
  footer?: React.ReactNode;
  wide?: boolean;
}

export declare function Modal(props: ModalProps): JSX.Element | null;
