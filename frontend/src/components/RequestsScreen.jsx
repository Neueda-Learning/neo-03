import React, { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  ChipGroup,
  DataTable,
  EmptyState,
  Grid,
  MetricTile,
  PageHeader,
  SearchInput,
  Toolbar,
} from '../design-system';
import { statusTone, STATUSES, time } from '../status.js';

const FILTERS = ['All', ...STATUSES];

/**
 * Everything this module has answered.
 *
 * ⚠️ Three columns, because the placeholder table behind it has three columns. When you replace
 * `demo_showcase` with your own table, this is the screen that shows it off — the operator UI is a
 * graded deliverable, so add the columns, filters and detail views your business topic needs.
 *
 * The board follows the platform shape (design-system/DESIGN.md § "Board"): a header stating the
 * screen's rules, a toolbar that narrows, a capped table. The 10-row cap and its footnote come from
 * DataTable — no screen re-implements them.
 */
export default function RequestsScreen({ requests, error, info }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');

  const counts = useMemo(
    () =>
      requests.reduce((acc, r) => {
        acc[r.status] = (acc[r.status] ?? 0) + 1;
        return acc;
      }, {}),
    [requests]
  );

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return requests.filter((r) => {
      if (filter !== 'All' && r.status !== filter) return false;
      if (!needle) return true;
      return r.applicationId.toLowerCase().includes(needle);
    });
  }, [requests, query, filter]);

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (r) => <Badge tone={statusTone(r.status)}>{r.status}</Badge>,
    },
    { key: 'createdAt', header: 'Answered', render: (r) => time(r.createdAt) },
  ];

  return (
    <>
      <PageHeader
        title="Applications"
        lede="everything the orchestrator has sent this module, and what it answered · newest first"
        meta={
          info
            ? `${info.serviceId} · ${info.domain} · v${info.version}` +
              (info.mockedDependencies?.length
                ? ` · mocking ${info.mockedDependencies.join(', ')}`
                : ' · nothing mocked')
            : undefined
        }
      />

      {error && (
        <Alert tone="negative" title="Could not load applications">
          {error} — the backend may still be starting. The list retries every two seconds.
        </Alert>
      )}

      <Grid cols={2} min={180} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Seen" value={requests.length} />
        <MetricTile label="Accepted" value={counts.ACCEPTED ?? 0} tone="positive" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search applications"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={matches}
        total={matches.length}
        rowKey={(r) => r.applicationId}
        footnote="newest first"
        empty={
          <EmptyState
            title={requests.length === 0 ? 'Nothing received yet' : 'No application matches that'}
          >
            {requests.length === 0 ? (
              <>
                Send one from the <strong>sidecar</strong> at <strong>localhost:9000</strong>, or turn
                the generator on in the orchestrator UI. Nothing in this screen sends applications —
                this module is called, it does not call itself.
              </>
            ) : (
              <>Clear the search, or pick a different status.</>
            )}
          </EmptyState>
        }
      />
    </>
  );
}
