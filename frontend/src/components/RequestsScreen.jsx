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
  SearchInput,
  Toolbar,
} from '../design-system';
import { statusLabel, statusTone, STATUSES, time } from '../status.js';
import { api } from '../api.js';
import { AttemptTrail } from './AttemptTrail.jsx';

const FILTERS = ['All', ...STATUSES];
const PAGE_SIZE = 10;

/**
 * One function, used by DataTable's `rowKey` AND by the expanded-row state.
 *
 * They have to agree exactly — DataTable expands the row whose key `===` `expandedKey` — and the
 * cheapest way to guarantee that is to have one definition rather than two that look alike.
 */
const rowKeyOf = (r) => r.kycId ?? r.applicationId;

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

  // The open row, and a cache of what each one has loaded.
  //
  // `expandedKey` MUST hold exactly what `rowKey` below returns — DataTable compares them with
  // ===, so storing an applicationId while rowKey yields a kycId renders nothing at all, with no
  // error and no visual. That is the one way to get this wrong quietly.
  const [expandedKey, setExpandedKey] = useState(null);
  const [attemptsByKey, setAttemptsByKey] = useState({});
  const [attemptsState, setAttemptsState] = useState({});

  // Fetched once per case and kept. Attempts are immutable — the ladder is written in a single
  // batch when the decision is made and never touched again — so re-fetching them on the board's
  // two-second poll would be pure noise.
  function toggleDetails(row) {
    const key = rowKeyOf(row);
    if (expandedKey === key) {
      setExpandedKey(null);
      return;
    }
    setExpandedKey(key);
    if (attemptsByKey[key] || attemptsState[key]?.loading) return;

    setAttemptsState((current) => ({ ...current, [key]: { loading: true } }));
    api
      .listAttempts(key)
      .then((attempts) => {
        setAttemptsByKey((current) => ({ ...current, [key]: attempts }));
        setAttemptsState((current) => ({ ...current, [key]: { loading: false } }));
      })
      .catch((err) => {
        setAttemptsState((current) => ({
          ...current,
          [key]: { loading: false, error: err.message },
        }));
      });
  }

  const counts = useMemo(
    () =>
      requests.reduce((acc, r) => {
        const label = statusLabel(r.status, r.decisionSource);
        acc[label] = (acc[label] ?? 0) + 1;
        return acc;
      }, {}),
    [requests]
  );

  const totalApproved = useMemo(
    () => requests.filter((request) => request.status === 'VERIFIED').length,
    [requests]
  );

  const matches = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return requests.filter((r) => {
      if (filter !== 'All' && statusLabel(r.status, r.decisionSource) !== filter) return false;
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
            <Badge tone={statusTone(r.status)}>{statusLabel(r.status, r.decisionSource)}</Badge>
          </button>
        ) : (
          <Badge tone={statusTone(r.status)}>{statusLabel(r.status, r.decisionSource)}</Badge>
        ),
    },
    { key: 'createdAt', header: 'Received', render: (r) => time(r.createdAt) },
    { key: 'updatedAt', header: 'Reviewed', render: (r) => (r.updatedAt ? time(r.updatedAt) : '-') },
    {
      key: 'details',
      header: 'Details',
      tight: true,
      // A real <button>, not a click on the <tr>. DataTable puts only onClick on the row — no
      // tabIndex, no key handler, no role — so a row-click affordance is mouse-only, which the
      // design system's accessibility rules do not allow. This is the same in-cell button idiom
      // the Status column already uses, and it gets the focus ring for free.
      render: (r) => {
        const key = rowKeyOf(r);
        const open = expandedKey === key;
        return (
          <button
            type="button"
            className="app-status-link"
            aria-expanded={open}
            onClick={() => toggleDetails(r)}
          >
            {open ? 'Hide details ▾' : 'View details ▸'}
          </button>
        );
      },
    },
  ];

  return (
    <>
      {error && (
        <Alert tone="negative" title="Could not load applications">
          {error} — the backend may still be starting. The list retries every two seconds.
        </Alert>
      )}

      <Grid cols={2} min={180} style={{ marginBottom: 'var(--ds-space-6)' }}>
        <MetricTile label="Total Applications" value={requests.length} />
        <MetricTile label="Total Approved" value={totalApproved} tone="positive" />
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
        className="app-requests-table"
        columns={columns}
        rows={pagedMatches}
        maxRows={null}
        total={matches.length}
        rowKey={rowKeyOf}
        expandedKey={expandedKey}
        renderExpanded={(r) => (
          <AttemptTrail
            record={r}
            attempts={attemptsByKey[rowKeyOf(r)]}
            loading={attemptsState[rowKeyOf(r)]?.loading}
            error={attemptsState[rowKeyOf(r)]?.error}
            thresholds={info?.idProvider}
          />
        )}
        footnote={`page ${page} of ${totalPages}`}
        footnoteOnly
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
