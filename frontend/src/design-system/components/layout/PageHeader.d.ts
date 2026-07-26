import * as React from 'react';

export interface PageHeaderProps extends React.HTMLAttributes<HTMLDivElement> {
  title: React.ReactNode;
  /** A `<Badge />` for the record's outcome, beside the title. */
  badge?: React.ReactNode;
  /** The rules this screen obeys, stated in one muted line beside the title. */
  lede?: React.ReactNode;
  /** The identity line under the title: applicant · product · submitted · config version. */
  meta?: React.ReactNode;
  actions?: React.ReactNode;
}

export declare function PageHeader(props: PageHeaderProps): JSX.Element;
