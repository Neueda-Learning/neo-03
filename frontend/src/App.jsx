import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import RequestsScreen from './components/RequestsScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

/**
 * The screens in the side menu.
 *
 * ⚠️ One real screen and three placeholders — the placeholders are there so the menu shows you
 * where your own screens go, and they are `disabled` so nobody clicks into nothing. Replace them
 * with what your business topic actually needs; the operator UI is a graded deliverable, and a
 * single read-only list is not one.
 */
const SCREENS = [
  { id: 'applications', label: 'Applications' },
  { id: 'cases', label: 'Cases', hint: 'your own table', disabled: true },
  { id: 'overrides', label: 'Overrides', hint: 'operator actions', disabled: true },
  { id: 'settings', label: 'Settings', hint: 'reference data', disabled: true },
];

/**
 * A sidebar rather than a top bar: this app is expected to grow more screens than a row of tabs
 * holds, and the menu is where a team plans that growth. The identity box above it is the only
 * place the app says who it belongs to — its values come from `/info`, so the same image reads
 * "Team 07" once SERVICE_TEAM says so.
 */
export default function App() {
  const [screen, setScreen] = useState('applications');
  const [requests, setRequests] = useState([]);
  const [error, setError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRequests(await api.listApplications());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
    const id = setInterval(reload, POLL_MS);
    return () => clearInterval(id);
  }, [reload]);

  const refreshHealth = useCallback(async () => {
    try {
      const [h, i] = await Promise.all([api.health(), api.info()]);
      setHealth(h);
      setInfo(i);
    } catch {
      setHealth(null);
    }
  }, []);

  useEffect(() => {
    refreshHealth();
    const id = setInterval(refreshHealth, HEALTH_MS);
    return () => clearInterval(id);
  }, [refreshHealth]);

  const up = !error && health?.status === 'UP';

  return (
    <AppShell
      side={
        <>
          <SideBrand
            brand={info?.team ?? 'Team'}
            product={info?.service ?? 'Module'}
            meta={info ? `${info.serviceId} · ${info.domain}` : undefined}
          />
          <SideNav items={SCREENS} active={screen} onSelect={setScreen} />
          {/* Health and refresh lived in the top bar; with the bar gone they belong beside the
              menu rather than inside it — a menu item that is not a screen is a trap. */}
          <div className="app-side-status">
            <StatusPill tone={up ? 'positive' : 'negative'}>{up ? 'Up' : 'Down'}</StatusPill>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                reload();
                refreshHealth();
              }}
            >
              Refresh
            </Button>
          </div>
        </>
      }
      footer="One of ten modules · applications arrive from the orchestrator, never from this UI"
    >
      {screen === 'applications' && (
        <RequestsScreen requests={requests} error={error} info={info} />
      )}
    </AppShell>
  );
}
