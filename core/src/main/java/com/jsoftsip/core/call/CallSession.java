package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.sip.SipPeer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregate root that owns the legs of one logical call.
 *
 * <p>A local call between two accounts of this application is one
 * logical call rendered as two {@link CallLeg} children (the outgoing
 * leg on the caller and the incoming leg on the callee). This session
 * is the single source of truth that groups them, derives the call
 * state by folding the leg states, and ends the partner leg when one
 * leg ends (preserving the baresip independent-leg-termination
 * invariant).
 *
 * <p>Observers register through {@link CallSessionListener} and
 * receive session-level change events instead of having to correlate
 * individual legs themselves.
 *
 * <p>This type lives in the UI-agnostic {@code core} module, so it does
 * not expose JavaFX properties. The UI layer mirrors {@link #isActive()}
 * and {@link #getState()} into JavaFX properties through the session
 * listener, which is what the dialer binds its controls to.
 */
public class CallSession {

    private final String key;

    private final List<CallLeg> legs = new CopyOnWriteArrayList<>();

    // Volatile because deriveState writes them under the session
    // monitor while plain readers (getState/isActive, UI mirror)
    // observe from other threads without locking
    private volatile CallState state = CallState.IDLE;

    private volatile boolean active = true;

    private final List<CallSessionListener> listeners = new CopyOnWriteArrayList<>();

    public CallSession(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public List<CallLeg> getLegs() {
        return List.copyOf(legs);
    }

    public CallState getState() {
        return state;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Adds a leg to the session, wires the leg back to this session
     * and re-derives the session state. Synchronized because the
     * session is the single mutation owner for its leg collection.
     */
    public synchronized void addLeg(CallLeg leg) {
        legs.add(leg);
        leg.setSession(this);
        deriveState();
    }

    /**
     * Removes a leg from the session, clears the leg back reference
     * so the detached leg stops routing mutations into this session,
     * and re-derives the session state. Synchronized because the
     * session is the single mutation owner for its leg collection.
     *
     * <p>A session left with no legs derives ENDED and inactive, because
     * folding an empty leg list makes the "all legs ended" predicate
     * vacuously true. This empty-means-ended semantic is the terminal
     * signal the dialer binding relies on to reset its controls, so it
     * must be preserved.
     */
    public synchronized void removeLeg(CallLeg leg) {
        legs.remove(leg);
        leg.setSession(null);
        deriveState();
    }

    /**
     * Ends every leg of the session other than {@code ended},
     * preserving the "end partner when one leg ends" invariant.
     * Used by the unit tests as the pure aggregate invariant, the
     * call service performs the equivalent side-effecting termination
     * (releasing the backend leg and recording history) through its
     * own lifecycle.
     */
    public synchronized void endPartnerOf(CallLeg ended) {
        List<CallLeg> partners = new ArrayList<>();

        for (CallLeg leg : legs) {
            if (leg != ended) {
                partners.add(leg);
            }
        }

        for (CallLeg partner : partners) {
            partner.setState(CallState.ENDED);

            if (partner.getEndedAt() == null) {
                partner.setEndedAt(LocalDateTime.now());
            }
        }

        deriveState();
    }

    /**
     * Derives the session state by folding the leg states, reusing
     * {@link CallStateMachine} semantics per leg: CONNECTED when any
     * leg is CONNECTED or HOLD, ENDED when all legs are ENDED,
     * otherwise the previously derived state is kept (DIALING /
     * RINGING / INCOMING). No new transition table is introduced.
     * Notifies the session listeners so the UI can mirror the change.
     *
     * <p>An empty leg list folds to ENDED and inactive (allMatch on an
     * empty list is vacuously true). This empty-means-ended semantic is
     * the terminal signal observers such as the dialer binding rely on.
     */
    public synchronized void deriveState() {
        boolean anyConnected = legs.stream().anyMatch(l -> l.getState() == CallState.CONNECTED
            || l.getState() == CallState.HOLD);

        boolean allEnded = legs.stream().allMatch(l -> l.getState() == CallState.ENDED);

        if (allEnded) {
            state = CallState.ENDED;
        } else if (anyConnected) {
            state = CallState.CONNECTED;
        }

        active = !allEnded;

        notifyListeners();
    }

    public void addSessionListener(CallSessionListener listener) {
        listeners.add(listener);
    }

    public void removeSessionListener(CallSessionListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (CallSessionListener listener : listeners) {
            listener.onSessionChanged(this);
        }
    }

    /**
     * Stable correlation key for a logical call: the two endpoints
     * (the local account and the dialed destination) reduced to their
     * normalized usernames, order-independent so A&lt,-&gt,B equals
     * B&lt,-&gt,A.
     *
     * <p>Reuses {@link SipPeer} normalization (usernames only, host
     * ignored) so a bare-number dial target with no host still correlates
     * with its hosted incoming counterpart. Including the host would split
     * one logical call across two sessions.
     *
     * @return the correlation key, or {@code null} when either endpoint
     *         cannot be parsed (in which case the caller mints a unique
     *         session for the leg).
     */
    public static String sessionKey(SipAccount account, String destination) {
        SipPeer local = SipPeer.parse(account.getUsername() + "@" + account.getDomain()).orElse(null);
        SipPeer remote = SipPeer.parse(destination).orElse(null);

        if (local == null || remote == null) {
            return null;
        }

        String a = local.getUsername();
        String b = remote.getUsername();

        return a.compareTo(b) <= 0 ? a + "#" + b : b + "#" + a;
    }
}
