import React, { useCallback, useEffect, useState } from 'react';
import { AppShell, Button, SideBrand, SideNav, StatusPill } from './design-system';
import RequestsScreen from './components/RequestsScreen.jsx';
import ReviewQueueScreen from './components/ReviewQueueScreen.jsx';
import { api } from './api.js';

const POLL_MS = 2000;
const HEALTH_MS = 10000;

/**
 * The screens in the side menu.
 *
 * ⚠️ One real screen and one placeholder — the placeholder is there so the menu shows you
 * where your own screens go, and they are `disabled` so nobody clicks into nothing. Replace them
 * with what your business topic actually needs; the operator UI is a graded deliverable, and a
 * single read-only list is not one.
 */
const SCREENS = [
  { id: 'applications', label: 'Applications' },
  { id: 'review-queue', label: 'Review Queue' },
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
  const [reviewQueue, setReviewQueue] = useState([]);
  const [reviewQueueError, setReviewQueueError] = useState(null);
  const [health, setHealth] = useState(null);
  const [info, setInfo] = useState(null);
  const [focusedReviewKycId, setFocusedReviewKycId] = useState(null);

  const reload = useCallback(async () => {
    try {
      setRequests(await api.listApplications());
      setError(null);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  const reloadReviewQueue = useCallback(async () => {
    try {
      setReviewQueue(await api.listReviewQueue());
      setReviewQueueError(null);
    } catch (e) {
      setReviewQueueError(e.message);
    }
  }, []);

  useEffect(() => {
    reload();
    reloadReviewQueue();
    const id = setInterval(() => {
      reload();
      reloadReviewQueue();
    }, POLL_MS);
    return () => clearInterval(id);
  }, [reload, reloadReviewQueue]);

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
      wide
      side={
        <>
          <SideBrand
            className="app-side-brand"
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
                reloadReviewQueue();
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
        <RequestsScreen
          requests={requests}
          error={error}
          info={info}
          onOpenReviewQueue={(kycId) => {
            setFocusedReviewKycId(kycId);
            setScreen('review-queue');
          }}
        />
      )}
      {screen === 'review-queue' && (
        <ReviewQueueScreen
          queue={reviewQueue}
          applications={requests}
          error={reviewQueueError}
          focusedKycId={focusedReviewKycId}
        />
      )}
    </AppShell>
  );
}
