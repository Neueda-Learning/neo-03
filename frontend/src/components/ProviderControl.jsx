import React, { useState } from 'react';
import { Caption, Section, StatusDot } from '../design-system';
import { api } from '../api.js';

/**
 * The provider control panel, in the sidebar.
 *
 * <p>This module's topic is "the provider is slow or down", and until now that could only be
 * demonstrated from the mock's own page — which does not exist in a deployed environment. The mock
 * runs as a container inside this service's ECS task with no route of its own, so on AWS the only
 * way to reach it is through the backend sitting beside it. That is what these buttons do.</p>
 *
 * <p><b>Presets, not dials.</b> The question anyone actually has is "show me what happens when
 * everything is rejected", not "set failureRatePct to 60". The raw dials are still on the mock's
 * own page for anyone who wants them.</p>
 *
 * <p>It changes what the PROVIDER says, never what this module decides. Every application still
 * travels the whole path — dispatch, retry ladder, callback — and the outcome is produced by the
 * real rules reading a real response.</p>
 */
const PRESETS = [
  { id: 'NORMAL', label: 'Normal', hint: 'the document decides' },
  { id: 'ALL_PASS', label: 'All pass', hint: 'every applicant verifies' },
  { id: 'ALL_REVIEW', label: 'All review', hint: 'every applicant parks' },
  { id: 'ALL_FAIL', label: 'All fail', hint: 'every applicant refused' },
  { id: 'FLAKY', label: 'Flaky', hint: 'random failures — trips the breaker' },
  { id: 'DOWN', label: 'Primary down', hint: 'fails over to the fallback', primaryOnly: true },
  { id: 'DOWN', label: 'Both down', hint: 'outage — everything parks', key: 'DOWN_ALL' },
];

export default function ProviderControl() {
  const [active, setActive] = useState(null);
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);

  async function apply(preset) {
    const key = preset.key ?? preset.id;
    setBusy(key);
    setError(null);
    try {
      await api.setProviderPreset(preset.id, Boolean(preset.primaryOnly));
      setActive(key);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(null);
    }
  }

  return (
    <Section title="Identity provider" className="app-provider">
      <div className="app-provider__list">
        {PRESETS.map((preset) => {
          const key = preset.key ?? preset.id;
          return (
            <button
              key={key}
              type="button"
              className={`app-provider__preset${active === key ? ' app-provider__preset--active' : ''}`}
              // The state is the MOCK's, not this component's — another tab, or the mock's own
              // page, can change it underneath us. So this says what was last asked for here,
              // and never claims to be the truth.
              aria-pressed={active === key}
              disabled={busy !== null}
              onClick={() => apply(preset)}
            >
              <span className="app-provider__label">
                {active === key && <StatusDot tone="positive" label="active" />}
                {preset.label}
              </span>
              <span className="app-provider__hint">{busy === key ? 'applying…' : preset.hint}</span>
            </button>
          );
        })}
      </div>
      {error && <Caption>could not reach the provider — {error}</Caption>}
    </Section>
  );
}
