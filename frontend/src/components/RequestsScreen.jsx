import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
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
const PAGE_SIZE = 10;

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
export default function RequestsScreen({ requests, error, info, onOpenReviewQueue }) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState('All');
  const [page, setPage] = useState(1);

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
      return [r.applicationId, r.name, r.documentId]
        .filter(Boolean)
        .some((value) => value.toLowerCase().includes(needle));
    });
  }, [requests, query, filter]);

  useEffect(() => {
    setPage(1);
  }, [query, filter]);

  const totalPages = Math.max(1, Math.ceil(matches.length / PAGE_SIZE));

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  const pagedMatches = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE;
    return matches.slice(start, start + PAGE_SIZE);
  }, [matches, page]);

  function openReviewQueue(request) {
    if (request.status === 'REVIEW') {
      onOpenReviewQueue?.(request.kycId);
    }
  }

  const columns = [
    { key: 'applicationId', header: 'Application', mono: true },
    { key: 'name', header: 'Applicant' },
    { key: 'type', header: 'Document type' },
    { key: 'documentId', header: 'Document ID', mono: true },
    { key: 'issuingCountry', header: 'Country', tight: true },
    { key: 'expiryDate', header: 'Expiry date' },
    {
      key: 'status',
      header: 'Status',
      tight: true,
      render: (r) =>
        r.status === 'REVIEW' ? (
          <button type="button" className="app-status-link" onClick={() => openReviewQueue(r)}>
            <Badge tone={statusTone(r.status)}>{r.status}</Badge>
          </button>
        ) : (
          <Badge tone={statusTone(r.status)}>{r.status}</Badge>
        ),
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
        <MetricTile label="Verified" value={counts.VERIFIED ?? 0} tone="positive" />
      </Grid>

      <Toolbar>
        <SearchInput
          grow
          placeholder="Application id, applicant, or document id"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search applications"
        />
        <ChipGroup options={FILTERS} value={filter} onChange={setFilter} counts={counts} />
      </Toolbar>

      <DataTable
        columns={columns}
        rows={pagedMatches}
        maxRows={null}
        total={matches.length}
        rowKey={(r) => r.kycId ?? r.applicationId}
        footnote={`newest first · page ${page} of ${totalPages}`}
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

      {matches.length > PAGE_SIZE && (
        <div className="app-pagination" aria-label="Applications pagination">
          <span className="app-pagination__summary">
            Showing {(page - 1) * PAGE_SIZE + 1}-{Math.min(page * PAGE_SIZE, matches.length)} of {matches.length}
          </span>
          <div className="app-pagination__actions">
            <Button disabled={page === 1} onClick={() => setPage((current) => current - 1)}>
              Previous
            </Button>
            <Button
              disabled={page === totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </>
  );
}
