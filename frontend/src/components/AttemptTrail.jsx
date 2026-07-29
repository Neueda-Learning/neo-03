import {
  Badge,
  BarChart,
  Caption,
  EmptyState,
  Spinner,
  Stack,
  Tag,
  Timeline,
} from '../design-system';
import { attemptTone, agencyRole, clockTime, gapSeconds, statusTone } from '../status';

/**
 * The evidence behind one application's answer.
 *
 * <p>The board can only show what a case was decided as. This shows how — which agency was asked,
 * in what order, how long each call took and how long the module waited between them. That matters
 * because a REVIEW caused by a doubtful applicant and a REVIEW caused by a dead provider look
 * identical on the board, and they are somebody else's problem in each case.</p>
 *
 * Composed entirely from existing design-system pieces: no new components, no new CSS. The row it
 * sits in is styled by `.ds-table__detail`, which the design system already ships.
 */
export function AttemptTrail({ record, attempts, loading, error, thresholds }) {
  if (loading) {
    return (
      <Stack gap={2} row>
        <Spinner />
        <Caption>Loading the provider calls for this case…</Caption>
      </Stack>
    );
  }

  if (error) {
    return (
      <EmptyState flush title="Could not load the attempts">
        {error} · the case is still on the board; only its evidence trail is unavailable.
      </EmptyState>
    );
  }

  // Zero attempts is a RESULT, not a gap in the data. Both local pre-checks — an expired document
  // and an issuing country that is not a code — decide without asking anyone, and the empty list
  // is the proof that no provider fee was paid for an answer the document itself already gave.
  if (!attempts || attempts.length === 0) {
    return (
      <EmptyState flush title="The provider was never called">
        This case was decided locally, so there is nothing to show here. That is the intended
        outcome when a document has expired or its issuing country is not a valid code — the bank
        does not pay for an answer it already has.
      </EmptyState>
    );
  }

  const answered = attempts.find((attempt) => attempt.result === 'ANSWERED');
  const failedOver = attempts.some((attempt) => agencyRole(attempt.agency) === 'fallback');
  const primaryCount = attempts.filter((a) => agencyRole(a.agency) === 'primary').length;

  return (
    <Stack gap={4}>
      <Stack gap={2} row>
        <Badge tone={statusTone(record.status)}>{record.status}</Badge>
        {/* A Tag, not a Badge: reason codes are machine tokens and the design system's rule is
            that those are monospaced and never coloured. The colour is the outcome's job. */}
        {record.reasonCode && <Tag>{record.reasonCode}</Tag>}
      </Stack>

      <Caption>{summarise(attempts, primaryCount, failedOver, answered)}</Caption>

      {answered && (
        <Stack gap={1}>
          <BarChart
            data={[{
              label: 'confidence',
              value: answered.confidence,
              tone: attemptTone('ANSWERED'),
            }]}
            max={100}
            labelWidth="7rem"
          />
          <Caption>{describeBands(answered.confidence, thresholds)}</Caption>
        </Stack>
      )}

      <Timeline items={attempts.map((attempt, index) => toItem(attempt, attempts[index - 1]))} />
    </Stack>
  );
}

/** The ladder in one sentence, so the shape is readable before any of the rows are. */
function summarise(attempts, primaryCount, failedOver, answered) {
  const parts = [`${attempts.length} ${attempts.length === 1 ? 'attempt' : 'attempts'}`];

  if (failedOver) {
    // Not stored anywhere — the backend's `failedOver` flag is transient and never persisted, so
    // the only record that a failover happened is the agency changing partway down this list.
    parts.push(`${primaryCount} on the primary, then the fallback`);
    parts.push(answered ? 'fallback answered' : 'neither answered');
  } else if (answered) {
    parts.push(attempts.length === 1 ? 'answered first time' : 'answered after retrying');
  } else {
    parts.push('no source answered');
  }

  return parts.join(' · ');
}

/** Where the score landed, in the bank's own words — thresholds come from /info, never hardcoded. */
function describeBands(confidence, thresholds) {
  if (!thresholds) return `confidence ${confidence}`;
  const { acceptThreshold, rejectThreshold } = thresholds;
  const band =
    confidence >= acceptThreshold ? 'at or above accept'
      : confidence <= rejectThreshold ? 'at or below reject'
        : 'between the thresholds — a human decides';
  return `confidence ${confidence} · reject ≤${rejectThreshold} · accept ≥${acceptThreshold} · ${band}`;
}

function toItem(attempt, previous) {
  const role = agencyRole(attempt.agency);
  const gap = gapSeconds(previous?.requestedAt, attempt.requestedAt);

  const facts = [];
  if (attempt.confidence != null) facts.push(`confidence ${attempt.confidence}`);
  // A SHORT_CIRCUITED attempt has a latency of 0 because no call was made. Printing "0 ms" would
  // read as an impossibly fast provider rather than an absent one.
  if (attempt.result !== 'SHORT_CIRCUITED' && attempt.latencyMs != null) {
    facts.push(`${attempt.latencyMs} ms`);
  }
  if (gap) facts.push(`waited ${gap}s`);

  return {
    id: attempt.attemptNumber,
    tone: attemptTone(attempt.result),
    // The outcome word stays exactly as the contract spells it — never prettified to
    // "Short circuited". It is the same token that is in the database and the logs.
    title: (
      <Stack gap={2} row>
        <Badge tone={attemptTone(attempt.result)} size="sm">{attempt.result}</Badge>
        <span>{attempt.agency ?? 'unknown source'}{role ? ` · ${role}` : ''}</span>
      </Stack>
    ),
    when: clockTime(attempt.requestedAt),
    detail: (
      <Stack gap={1}>
        {facts.length > 0 && <span>{facts.join(' · ')}</span>}
        {attempt.comment && <span>{attempt.comment}</span>}
        {/* Wrapped in a row Stack so the Tag hugs its text. A Tag placed directly in a column
            Stack is stretched to the full width by flex's default align-items, which turns a
            short machine token into a full-width bar that reads like an input field. */}
        {attempt.providerRef && (
          <Stack gap={2} row>
            <Tag>{attempt.providerRef}</Tag>
          </Stack>
        )}
      </Stack>
    ),
  };
}
