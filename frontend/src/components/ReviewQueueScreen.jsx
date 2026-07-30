import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DataTable,
  EmptyState,
  Field,
  PageHeader,
  Textarea,
} from '../design-system';
import { api } from '../api.js';
import { AttemptTrail } from './AttemptTrail.jsx';
import { useAttempts } from '../useAttempts.js';

const PAGE_SIZE = 10;

function queueAge(createdAt) {
  const elapsedMs = Math.max(0, Date.now() - new Date(createdAt).getTime());
  const minutes = Math.floor(elapsedMs / 60000);
  if (minutes < 60) return `${Math.max(minutes, 1)}m in queue`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h in queue`;
  return `${Math.floor(hours / 24)}d in queue`;
}

function workings(item) {
  const confidence = item.confidence == null ? 'No provider confidence' : `Confidence ${item.confidence}`;
  return [item.source, confidence, item.comment].filter(Boolean).join(' - ');
}

export default function ReviewQueueScreen({ queue, applications, error, focusedKycId, info }) {
  const [selectedId, setSelectedId] = useState(null);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [actionMessage, setActionMessage] = useState(null);
  const [page, setPage] = useState(1);

  // The same panel the board uses, so an analyst does not have to leave the queue to find out
  // WHY a case is parked — which is the first thing they need and the whole reason it is here.
  const attempts = useAttempts();

  const applicantNames = useMemo(
    () => new Map(applications.map((application) => [application.applicationId, application.name])),
    [applications]
  );

  useEffect(() => {
    if (!queue.some((item) => item.kycId === selectedId)) {
      setSelectedId(queue[0]?.kycId ?? null);
      setReason('');
    }
  }, [queue, selectedId]);

  useEffect(() => {
    if (!focusedKycId) {
      return;
    }

    const index = queue.findIndex((item) => item.kycId === focusedKycId);
    if (index === -1) {
      return;
    }

    setSelectedId(focusedKycId);
    setPage(Math.floor(index / PAGE_SIZE) + 1);
    setReason('');
    setActionError(null);
    setActionMessage(null);
  }, [focusedKycId, queue]);

  const totalPages = Math.max(1, Math.ceil(queue.length / PAGE_SIZE));

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  const pagedQueue = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE;
    return queue.slice(start, start + PAGE_SIZE);
  }, [queue, page]);

  const selected = queue.find((item) => item.kycId === selectedId) ?? null;
  const canSubmit = reason.trim().length > 0 && !submitting;

  async function submit(decision) {
    if (!selected || !canSubmit) return;

    setSubmitting(true);
    setActionError(null);
    setActionMessage(null);
    try {
      await api.recordReviewDecision(selected.kycId, {
        source: selected.source,
        decision,
        comment: reason.trim(),
      });
      setReason('');
      setActionMessage(
        `Case ${selected.applicationId} was ${decision === 'ACCEPTED' ? 'approved' : 'declined'}.`
      );
    } catch (e) {
      setActionError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  // The queue row carries the review, not the decision. The outcome and its reason code live on
  // the board row for the same case, which App already has loaded.
  const recordsByKycId = useMemo(
    () => new Map(applications.map((record) => [record.kycId, record])),
    [applications]
  );

  const columns = [
    { key: 'applicationId', header: 'Reference', mono: true },
    {
      key: 'applicant',
      header: 'Applicant',
      render: (item) => applicantNames.get(item.applicationId) || '-',
    },
    { key: 'age', header: 'Age', render: (item) => queueAge(item.createdAt) },
    {
      key: 'state',
      header: 'State',
      tight: true,
      render: () => <Badge tone="warning">WAITING</Badge>,
    },
    {
      key: 'details',
      header: 'Evidence',
      tight: true,
      // A real <button>, not the row click: the row click already means "select this case for the
      // panel on the right", and one gesture cannot mean two things. It is also the only way the
      // affordance is reachable by keyboard — DataTable puts no key handler on the <tr>.
      render: (item) => (
        <button
          type="button"
          className="app-status-link"
          aria-expanded={attempts.isOpen(item.kycId)}
          onClick={() => attempts.toggle(item.kycId)}
        >
          {attempts.isOpen(item.kycId) ? 'Hide ▾' : 'Evidence ▸'}
        </button>
      ),
    },
  ];

  return (
    <div className="review-queue-screen">
      <PageHeader title="Work queue" lede="oldest first - select a case to inspect its evidence" />

      {error && (
        <Alert tone="negative" title="Could not load review queue">
          {error} - the queue retries every two seconds.
        </Alert>
      )}
      {actionError && (
        <Alert tone="negative" title="Could not submit decision">
          {actionError}
        </Alert>
      )}
      {actionMessage && (
        <Alert tone="positive" title="Decision recorded">
          {actionMessage}
        </Alert>
      )}

      <div className="review-queue-layout">
        <section aria-label="Review queue">
          <DataTable
            columns={columns}
            rows={pagedQueue}
            maxRows={null}
            total={queue.length}
            rowKey={(item) => item.kycId}
            selectedKey={selectedId}
            onRowClick={(item) => {
              setSelectedId(item.kycId);
              setReason('');
              setActionError(null);
              setActionMessage(null);
            }}
            expandedKey={attempts.expandedKey}
            renderExpanded={(item) => (
              <AttemptTrail
                record={recordsByKycId.get(item.kycId) ?? { status: item.reviewResult }}
                attempts={attempts.attemptsFor(item.kycId)}
                loading={attempts.loadingFor(item.kycId)}
                error={attempts.errorFor(item.kycId)}
                thresholds={info?.idProvider}
              />
            )}
            footnote={`page ${page} of ${totalPages}`}
            footnoteOnly
            empty={
              <EmptyState title="Queue clear">
                No cases are waiting for an analyst decision.
              </EmptyState>
            }
          />

          {queue.length > PAGE_SIZE && (
            <div className="app-pagination" aria-label="Review queue pagination">
              <span className="app-pagination__summary">
                Showing {(page - 1) * PAGE_SIZE + 1}-{Math.min(page * PAGE_SIZE, queue.length)} of {queue.length}
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
        </section>

        <aside className="review-case" aria-label="Selected review case">
          {selected ? (
            <>
              <div className="review-case__heading">
                <h2>Selected case</h2>
                <span className="review-case__reference">{selected.applicationId}</span>
              </div>

              <dl className="review-case__facts">
                <div>
                  <dt>System outcome</dt>
                  <dd>{selected.reviewResult}</dd>
                </div>
                <div>
                  <dt>Workings</dt>
                  <dd>{workings(selected)}</dd>
                </div>
                <div>
                  <dt>Queued</dt>
                  <dd>{queueAge(selected.createdAt)}</dd>
                </div>
                <div>
                  <dt>Your decision</dt>
                  <dd>-</dd>
                </div>
              </dl>

              <Field label="Reason" required hint="Explain the analyst decision for the case record.">
                {({ id, describedBy }) => (
                  <Textarea
                    id={id}
                    aria-describedby={describedBy}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    rows={3}
                  />
                )}
              </Field>

              <div className="review-case__actions">
                <Button
                  disabled={!canSubmit}
                  busy={submitting}
                  busyLabel="Submitting..."
                  onClick={() => submit('ACCEPTED')}
                >
                  Approve
                </Button>
                <Button
                  variant="danger"
                  disabled={!canSubmit}
                  busy={submitting}
                  busyLabel="Submitting..."
                  onClick={() => submit('REJECTED')}
                >
                  Decline
                </Button>
              </div>
            </>
          ) : (
            <EmptyState title="No case selected">
              Select a waiting case to inspect the verification result.
            </EmptyState>
          )}
        </aside>
      </div>
    </div>
  );
}
