import {
  Badge,
  BarChart,
  Caption,
  EmptyState,
  Spinner,
  Stack,
  Tag,
} from '../design-system';
import {
  attemptTone,
  agencyRole,
  clockTime,
  confidenceBand,
  confidenceTone,
  gapSeconds,
  statusTone,
} from '../status';

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
              // The bar is coloured by the BAND, not by the fact that somebody answered. It used
              // to be green whenever the provider replied, so a confidence of 41 — a rejection —
              // drew the same reassuring green as a 99.
              tone: confidenceTone(confidenceBand(answered.confidence, thresholds)),
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
          <li
            className="app-rail__step"
            key={attempt.attemptNumber}
            // The full record, for anyone who wants the exact instant. Kept off the face of the
            // rail because the gaps on the connectors already tell the timing story, and five
            // stacked lines per dot buried the two facts that matter.
            title={`Attempt ${attempt.attemptNumber} · ${clockTime(attempt.requestedAt)}`
              + (attempt.providerRef ? ` · ${attempt.providerRef}` : '')
              + (attempt.comment ? `\n${attempt.comment}` : '')}
          >
            {/* Line — label — line, as three flex children. The label is not painted OVER the
                connector with a background: under the glass theme --ds-color-surface is literally
                `transparent`, so a mask hides nothing and the rule runs straight through the text.
                Making the gap structural means it cannot fail on any background. */}
            {index > 0 && (
              <span className="app-rail__link">
                {/* The rules are decoration and are hidden from assistive tech; the wait itself is
                    NOT — it is a fact about the ladder that appears nowhere else on the page. */}
                <span className="app-rail__line" aria-hidden="true" />
                {gap && (
                  <>
                    <span className="app-rail__wait">waiting {gap}s</span>
                    <span className="app-rail__line" aria-hidden="true" />
                  </>
                )}
              </span>
            )}

            {/* The dot is a mark, not a label — the target and the outcome sit under it, so
                nothing here depends on reading a colour. */}
            <span className={`app-rail__dot app-rail__dot--${attemptTone(attempt.result)}`} />

            {/* WHO was called, first and unmuted. It is the question a ladder is read to answer:
                the outcome only means something once you know which source produced it. */}
            <span className="app-rail__target">{targetOf(attempt, handover)}</span>

            <span className={`app-rail__outcome app-rail__outcome--${attemptTone(attempt.result)}`}>
              {attempt.result}
              <span className="app-rail__cost"> · {costOf(attempt)}</span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}

/** The source this attempt was aimed at, and — when it changes — that the chain moved on. */
function targetOf(attempt, handover) {
  const agency = attempt.agency ?? 'unknown source';
  return handover ? `${agency} · fallback` : agency;
}

/**
 * What the attempt cost.
 *
 * A SHORT_CIRCUITED attempt says "not called" in words rather than showing a dash or "0 ms". Its
 * latency is zero because no request left the module, and a dash leaves the reader to work out
 * why — which is exactly the question this label exists to answer.
 */
function costOf(attempt) {
  if (attempt.result === 'SHORT_CIRCUITED') return 'not called';
  const facts = [];
  if (attempt.confidence != null) facts.push(`confidence ${attempt.confidence}`);
  if (attempt.latencyMs != null) facts.push(`${attempt.latencyMs} ms`);
  return facts.join(' · ') || 'no timing recorded';
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
  const said = {
    accept: 'at or above accept',
    reject: 'at or below reject',
    review: 'between the thresholds — a human decides',
  }[confidenceBand(confidence, thresholds)];
  return `confidence ${confidence} · reject ≤${rejectThreshold} · accept ≥${acceptThreshold} · ${said}`;
}
