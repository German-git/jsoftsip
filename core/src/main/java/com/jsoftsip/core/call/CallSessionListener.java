package com.jsoftsip.core.call;

/**
 * Observer for session-level change events. Replaces the need for
 * callers to correlate individual {@link CallLeg} deltas: a single
 * {@link CallSession} change is delivered whenever any of its legs
 * transitions.
 */
public interface CallSessionListener {

    void onSessionChanged(CallSession session);
}
