import * as React from 'react';

export interface FieldRenderArgs {
  id: string;
  invalid: boolean;
  describedBy?: string;
}

export interface FieldProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'children'> {
  label?: React.ReactNode;
  /** Static guidance shown under the control. */
  hint?: React.ReactNode;
  /** The field-level reason. Present means invalid. */
  error?: React.ReactNode;
  required?: boolean;
  /** Supply your own control id; otherwise one is generated and wired up. */
  htmlFor?: string;
  /**
   * Prefer the render-prop form — it hands you the generated `id` (and `invalid`,
   * `describedBy`) to spread onto the control, which is what associates the label.
   * A plain node child must supply `htmlFor` itself, or the label stays unassociated.
   */
  children?: React.ReactNode | ((args: FieldRenderArgs) => React.ReactNode);
}

export declare function Field(props: FieldProps): JSX.Element;
