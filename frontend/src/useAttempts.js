import { useState } from 'react';
import { api } from './api.js';

/**
 * The expandable attempt panel, shared by the board and the review queue.
 *
 * <p>Both screens want the same behaviour — one row open at a time, fetched on first open and
 * then kept — and it is about thirty lines of state each. Copied twice it drifts: one screen
 * gains a loading state the other lacks, or one forgets that the key must match `rowKey`.</p>
 *
 * <p><b>Fetched once and cached deliberately.</b> Attempts are immutable — a case's ladder is
 * written in a single batch when the decision is made and never touched again — so re-fetching
 * them on either screen's two-second poll would be pure noise.</p>
 */
export function useAttempts() {
  const [expandedKey, setExpandedKey] = useState(null);
  const [attemptsByKey, setAttemptsByKey] = useState({});
  const [stateByKey, setStateByKey] = useState({});

  /**
   * @param key the row's key — MUST be whatever that table's `rowKey` returns. DataTable
   *            compares it with `===`, so a mismatch renders nothing at all, silently.
   */
  function toggle(key) {
    if (expandedKey === key) {
      setExpandedKey(null);
      return;
    }
    setExpandedKey(key);
    if (attemptsByKey[key] || stateByKey[key]?.loading) return;

    setStateByKey((current) => ({ ...current, [key]: { loading: true } }));
    api
      .listAttempts(key)
      .then((attempts) => {
        setAttemptsByKey((current) => ({ ...current, [key]: attempts }));
        setStateByKey((current) => ({ ...current, [key]: { loading: false } }));
      })
      .catch((err) => {
        setStateByKey((current) => ({
          ...current,
          [key]: { loading: false, error: err.message },
        }));
      });
  }

  return {
    expandedKey,
    toggle,
    isOpen: (key) => expandedKey === key,
    attemptsFor: (key) => attemptsByKey[key],
    loadingFor: (key) => stateByKey[key]?.loading,
    errorFor: (key) => stateByKey[key]?.error,
  };
}
