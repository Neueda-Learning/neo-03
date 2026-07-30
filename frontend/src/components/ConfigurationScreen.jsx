import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Badge,
  Caption,
  ChipGroup,
  DataTable,
  EmptyState,
  KeyValue,
  PageHeader,
  Section,
  Toolbar,
} from '../design-system';
import { api } from '../api.js';

const POLL_MS = 3000;

/**
 * <h2>The provider control panel, and the numbers that decide an outcome.</h2>
 *
 * <p>Two of the design system's archetypes at once, deliberately: the presets are <b>Panel</b>
 * ("make a dependency misbehave — a ChipGroup for mode"), and the tables under them are
 * <b>Config</b> ("a DataTable of current values"). One is what you can change, the other is what
 * is currently true.</p>
 *
 * <p><b>Why this screen exists at all.</b> The mocked agencies have a control page of their own,
 * and in a deployed environment it is unreachable — the mock runs as a container inside this
 * service's ECS task with no route, so only the backend beside it can talk to it. Without this
 * screen the module's own topic, "the provider is slow or down", cannot be demonstrated anywhere
 * but a laptop.</p>
 */

/** Each preset, and the state of the mock that means "this one is selected". */
const PRESETS = [
  { id: 'NORMAL', label: 'Normal', hint: 'the document decides its own score' },
  { id: 'ALL_PASS', label: 'All pass', hint: 'every applicant verifies' },
  { id: 'ALL_REVIEW', label: 'All review', hint: 'every applicant parks for a human' },
  { id: 'ALL_FAIL', label: 'All fail', hint: 'every applicant is refused on confidence' },
  { id: 'FLAKY', label: 'Flaky', hint: 'random failures — enough to trip the circuit breaker' },
  { id: 'PRIMARY_DOWN', label: 'Primary down', hint: 'the fallback answers instead' },
  { id: 'BOTH_DOWN', label: 'Both down', hint: 'an outage — every application parks' },
];

const LABELS = PRESETS.map((p) => p.label);

/** What the module has to be told, for presets whose name is not the mock's own vocabulary. */
const REQUESTS = {
  PRIMARY_DOWN: { preset: 'DOWN', primaryOnly: true },
  BOTH_DOWN: { preset: 'DOWN', primaryOnly: false },
};

export default function ConfigurationScreen({ info }) {
  const [config, setConfig] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setConfig(await api.providerConfig());
      setError(null);
    } catch (err) {
      setError(err.message);
    }
  }, []);

  // Polled, so this screen reports what the mock IS rather than what was last asked for here.
  // The mock's own page and any other tab can change it underneath us, and a control that lies
  // about the thing it controls is worse than one that shows nothing.
  useEffect(() => {
    load();
    const timer = setInterval(load, POLL_MS);
    return () => clearInterval(timer);
  }, [load]);

  async function apply(label) {
    const preset = PRESETS.find((p) => p.label === label);
    if (!preset) return;
    const request = REQUESTS[preset.id] ?? { preset: preset.id, primaryOnly: false };
    setBusy(true);
    try {
      setConfig(await api.setProviderPreset(request.preset, request.primaryOnly));
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const active = config ? PRESETS.find((p) => p.id === presetOf(config))?.label : null;
  const selected = PRESETS.find((p) => p.label === active);

  const agencies = config
    ? Object.entries(config.agencies).map(([slug, agency]) => ({ slug, ...agency }))
    : [];

  const columns = [
    {
      key: 'agency',
      header: 'Source',
      render: (a) => (
        <>
          {a.agency} <Caption>{a.slug === 'national' ? 'primary' : 'fallback'}</Caption>
        </>
      ),
    },
    {
      key: 'answering',
      header: 'Answering',
      tight: true,
      render: (a) => (
        <Badge tone={a.killSwitch ? 'negative' : a.failureRatePct > 0 ? 'info' : 'positive'}>
          {a.killSwitch ? 'REFUSING' : a.failureRatePct > 0 ? `${a.failureRatePct}% DROPPED` : 'UP'}
        </Badge>
      ),
    },
    { key: 'answerMode', header: 'Answers with', mono: true },
    { key: 'latencyMs', header: 'Latency', render: (a) => `${a.latencyMs} ms` },
    { key: 'calls', header: 'Calls', numeric: true, render: (a) => config.calls?.[a.slug] ?? 0 },
  ];

  return (
    <>
      <PageHeader
        title="Configuration"
        lede="what the mocked identity agencies answer, and the thresholds that judge them"
        badge={busy ? <Badge tone="info">applying</Badge> : undefined}
      />

      {error && (
        <Alert tone="negative" title="Could not reach the identity agencies">
          {error} — the module proxies this to a container beside it, so this failing usually
          means the mock is not running rather than that the network is wrong.
        </Alert>
      )}

      <Section
        title="Identity provider"
        aside={<Caption>changes take effect on the next application</Caption>}
      >
        <Toolbar>
          <ChipGroup options={LABELS} value={active} onChange={apply} />
        </Toolbar>

        <Caption>
          {selected
            ? selected.hint
            : 'the dials do not match a preset — someone has set them from the provider’s own page'}
        </Caption>

        <DataTable
          columns={columns}
          rows={agencies}
          maxRows={null}
          rowKey={(a) => a.slug}
          footnoteOnly
          empty={<EmptyState title="No provider" flush>The identity agencies are not answering.</EmptyState>}
        />
      </Section>

      <Section title="Decision thresholds">
        <KeyValue
          items={[
            { label: 'Verify at or above', value: info?.idProvider?.acceptThreshold ?? '—', mono: true },
            { label: 'Refuse at or below', value: info?.idProvider?.rejectThreshold ?? '—', mono: true },
            { label: 'Between the two', value: 'parked for a human' },
          ]}
        />
        {/* Read-only on purpose. Thresholds are compliance policy: they are configuration so the
            risk team can move them without a deploy, not so an operator can move them mid-shift.
            A case decided under one set has to keep explaining itself under the next. */}
        <Caption>
          Set by configuration, not from this screen — they are compliance policy, and a case
          decided under one set of thresholds must still read correctly under the next.
        </Caption>
      </Section>
    </>
  );
}

/**
 * Which preset the mock is currently in — derived from its dials, not remembered from a click.
 *
 * <p>Returns null when nothing matches, which is a real state: the mock's own page can set any
 * combination, and claiming one of ours would be a lie about the thing this screen controls.</p>
 */
function presetOf(config) {
  const national = config.agencies?.national;
  const tax = config.agencies?.tax;
  if (!national || !tax) return null;

  if (national.killSwitch && tax.killSwitch) return 'BOTH_DOWN';
  if (national.killSwitch && !tax.killSwitch) return 'PRIMARY_DOWN';
  if (tax.killSwitch) return null; // fallback down alone is not a preset anyone offers
  if (national.failureRatePct > 0 || tax.failureRatePct > 0) return 'FLAKY';
  if (national.latencyMs > 0 || tax.latencyMs > 0) return null; // a latency dial, set elsewhere
  if (national.answerMode !== tax.answerMode) return null;
  return national.answerMode;
}
