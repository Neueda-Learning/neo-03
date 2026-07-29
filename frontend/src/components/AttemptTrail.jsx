import {
  Badge,
  BarChart,
  Caption,
  EmptyState,
  Spinner,
  Stack,
  Tag,
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
 * Built from design-system components plus one app-owned shape, the horizontal rail below. The row
 * it sits in is styled by `.ds-table__detail`, which the design system already ships.
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

      <AttemptRail attempts={attempts} />
    </Stack>
  );
}

/**
 * The ladder as dots on a horizontal line — left to right, in the order the calls went out.
 *
 * <p>A row of dots reads as one shape: you see "three then one, and the last is green" before
 * reading a single word, which is the question the panel exists to answer. The vertical list this
 * replaced showed the same facts but made you assemble the shape yourself.</p>
 *
 * <p><b>Every dot is labelled.</b> The design system's own {@code StepTrail} is a row of dots with
 * hover titles, which is right for a whole journey in a table cell and wrong here — the latency
 * and the wait between attempts are the point, and a fact you have to hover to find is a fact
 * most people never see. So this is the app's own component, built from {@code --ds-*} tokens
 * only, which is what DESIGN.md §8 asks you to do when the system does not have the shape.</p>
 *
 * <p>The connector carries the wait, so the backoff is drawn between the attempts it separates
 * rather than listed under one of them.</p>
 */
function AttemptRail({ attempts }) {
  return (
    <ol className="app-rail" aria-label="Provider attempts, in order">
      {attempts.map((attempt, index) => {
        const previous = attempts[index - 1];
        const gap = gapSeconds(previous?.requestedAt, attempt.requestedAt);
        const role = agencyRole(attempt.agency);
        // Where the primary's budget ran out and the fallback was tried. It is the only visible
        // record of a failover — the backend's flag for it is transient and never stored.
        const handover = previous && agencyRole(previous.agency) !== role;

        return (
          <li className="app-rail__step" key={attempt.attemptNumber}>
            {index > 0 && (
              <span className="app-rail__link" aria-hidden="true">
                <span className="app-rail__wait">{gap ? `${gap}s` : ''}</span>
              </span>
            )}

            {/* The dot is a mark, not a label — the number and the result word sit under it, so
                nothing here depends on reading a colour, and no text has to survive being placed
                on a strong accent fill. */}
            <span className={`app-rail__dot app-rail__dot--${attemptTone(attempt.result)}`} />

            <span className="app-rail__number">#{attempt.attemptNumber}</span>
            <span className={`app-rail__result app-rail__result--${attemptTone(attempt.result)}`}>
              {attempt.result}
            </span>

            <span className="app-rail__agency">
              {handover && <span className="app-rail__handover">failover · </span>}
              {attempt.agency ?? 'unknown'}
            </span>

            <span className="app-rail__facts">{factsFor(attempt)}</span>
            <span className="app-rail__when">{clockTime(attempt.requestedAt)}</span>
          </li>
        );
      })}
    </ol>
  );
}

function factsFor(attempt) {
  const facts = [];
  if (attempt.confidence != null) facts.push(`confidence ${attempt.confidence}`);
  // A SHORT_CIRCUITED attempt has a latency of 0 because no call was made. Printing "0 ms" would
  // read as an impossibly fast provider rather than an absent one.
  if (attempt.result !== 'SHORT_CIRCUITED' && attempt.latencyMs != null) {
    facts.push(`${attempt.latencyMs} ms`);
  }
  return facts.join(' · ') || '—';
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
