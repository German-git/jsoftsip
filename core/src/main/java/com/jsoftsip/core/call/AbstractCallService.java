package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipPeer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base implementation of {@link CallService} that holds the shared
 * call tracking state and the call lifecycle methods common to all
 * backends.
 *
 * <p>Subclasses must implement {@link #startCall}, {@link #holdCall}
 * and {@link #resumeCall} because those operations differ between
 * real and mock SIP clients. They may override the protected hooks
 * {@link #onIncomingCallStarted}, {@link #onIncomingCallCreated},
 * {@link #scheduleIncomingCallTimeout}, {@link #onFinishingCall},
 * {@link #onCallFinished}, {@link #isForkedIncomingCall} and
 * {@link #applyCallEvent} to inject backend-specific behavior
 * without duplicating the lifecycle logic. Outgoing leg registration
 * ({@link #registerOutgoingCall}) and the event-processing skeleton
 * ({@link #processCallEvent}) are shared template methods so every
 * backend attaches legs to sessions and drives state transitions
 * through one single path.
 *
 * <p>Threading contract: ctrl_tcp call events arrive on the single
 * backend reader thread while UI actions arrive from the JavaFX
 * thread or the ui executor. Every state-mutating entry point runs
 * its whole check-then-act sequence under the monitor of the owning
 * {@link CallSession}, falling back to the {@link CallLeg} instance
 * when no session is attached yet, so a backend event can never
 * interleave with a UI action mid-lifecycle. Because both legs of a
 * peer-local call share one session, the partner recursion of
 * {@link #finishCall} re-enters the same monitor instead of nesting
 * distinct locks. Listener notifications run outside that monitor
 * except where documented otherwise (see {@link #finishCall}).
 */
public abstract class AbstractCallService implements CallService {

    protected final SipClient sipClient;

    protected final AccountService accountService;

    protected final HistoryService historyService;

    protected final List<CallLeg> activeCalls = new CopyOnWriteArrayList<>();

    protected final List<CallListener> listeners = new CopyOnWriteArrayList<>();

    protected final ConcurrentHashMap<String, CallLeg> callsByBackendId = new ConcurrentHashMap<>();

    /**
     * Sessions keyed by their correlation key. Groups the legs of one
     * logical call so the partner leg can be found without scanning the
     * flat {@link #activeCalls} list. Populated incrementally as legs
     * are created, see {@link #getOrCreateSession}.
     */
    protected final ConcurrentHashMap<String, CallSession> sessionsByKey = new ConcurrentHashMap<>();

    protected AbstractCallService(SipClient sipClient, AccountService accountService, HistoryService historyService) {

        this.sipClient = sipClient;
        this.accountService = accountService;
        this.historyService = historyService;
    }

    @Override
    public List<CallLeg> getActiveCalls() {

        return List.copyOf(activeCalls);
    }

    @Override
    public void addListener(CallListener listener) {

        listeners.add(listener);
    }

    @Override
    public void removeListener(CallListener listener) {

        listeners.remove(listener);
    }

    @Override
    public void endCall(String callId) {

        findCall(callId).ifPresent(call ->

        sipClient.endCall(call.getBackendCallId()));
    }

    @Override
    public void answerCall(String callId) {

        findCall(callId).ifPresent(call ->

        sipClient.answerCall(call.getBackendCallId()));
    }

    @Override
    public void rejectCall(String callId) {

        findCall(callId).ifPresent(call -> {

            synchronized (stateMonitorOf(call)) {

                call.setResult(CallResult.REJECTED);

                sipClient.rejectCall(call.getBackendCallId());
            }
        });
    }

    @Override
    public void setVolume(int volume) {

        sipClient.setVolume(volume);
    }

    @Override
    public void setMicrophoneVolume(int volume) {

        sipClient.setMicrophoneVolume(volume);
    }

    @Override
    public void setMicrophoneMuted(boolean muted) {

        sipClient.setMicrophoneMuted(muted);
    }

    @Override
    public boolean isVideoSupported() {

        return sipClient != null;
    }

    @Override
    public boolean setVideoTransmissionEnabled(boolean enabled) {

        return sipClient.setVideoTransmissionEnabled(enabled);
    }

    protected Optional<CallLeg> findCall(String backendCallId) {

        return Optional.ofNullable(callsByBackendId.get(backendCallId));
    }

    /**
     * Monitor guarding the check-then-act sequences of the given
     * leg: the owning session when present, so every leg of one
     * logical call shares a single lock, or the leg itself when it
     * has not been attached to a session yet.
     */
    protected static Object stateMonitorOf(CallLeg call) {

        CallSession session = call.getSession();

        return session != null ? session : call;
    }

    /**
     * Returns the existing session for the given correlation key, or
     * creates and stores a new one. A {@code null} key (an endpoint
     * that could not be parsed) mints a unique session so the leg still
     * belongs to a session of its own.
     */
    protected CallSession getOrCreateSession(String key) {
        if (key == null) {
            key = "ext-" + java.util.UUID.randomUUID();
        }

        return sessionsByKey.computeIfAbsent(key, CallSession::new);
    }

    protected void notifyListeners(CallLeg call) {

        listeners.forEach(listener -> listener.onCallChanged(call));
    }

    /**
     * Finishes a call: resolves the result, transitions to ENDED,
     * updates the ended timestamp, removes it from the active
     * collections (including its session, which is evicted once no
     * legs remain) and records it in the history service.
     *
     * <p>Hooks:
     * <ul>
     *   <li>{@link #onFinishingCall} is invoked before the state
     *       changes (useful for logging).</li>
     *   <li>{@link #onCallFinished} is invoked after the call has
     *       been removed from the active collections (useful for
     *       final notifications).</li>
     * </ul>
     *
     * <p>The whole teardown, including history registration and the
     * partner search with its recursion, runs under the owning
     * session monitor so a concurrent termination can never pass the
     * ENDED guard twice nor register the call in the history twice.
     * The listener notification deliberately stays inside that
     * monitor: the only production {@link CallListener} defers its
     * UI work through Platform.runLater (ActiveCallsPaneController)
     * and cannot synchronously re-enter this service, which was
     * verified for this locking design.
     */
    protected final void finishCall(CallLeg call, String backendCallId) {

        synchronized (stateMonitorOf(call)) {

            doFinishCall(call, backendCallId);
        }
    }

    /**
     * Teardown body of {@link #finishCall}. Runs under the monitor
     * captured by the caller, including the recursive partner finish
     * at the end, so every leg of one logical call is torn down
     * inside a single critical section even when a divergent
     * correlation key left it in a different session.
     */
    private void doFinishCall(CallLeg call, String backendCallId) {

        if (call.getState() == CallState.ENDED) {

            return;
        }

        onFinishingCall(call, backendCallId);

        if (call.getResult() == null) {

            call.setResult(call.resolveResult());
        }

        call.setState(CallState.ENDED);

        call.setEndedAt(LocalDateTime.now());

        activeCalls.remove(call);

        callsByBackendId.remove(backendCallId);

        if (historyService != null) {

            historyService.registerFinishedCall(call);
        }

        onCallFinished(call);

        // Notify listeners (dialer button state, call cards, ...) that
        // this call ended. The session below ends the partner leg
        // through the same lifecycle, so it is notified too.
        notifyListeners(call);

        // Evict the finished leg from its session: a stale ENDED leg would
        // linger in the session otherwise, and a redial to the same
        // destination would reuse that session, letting first-leg lookups
        // hit the dead leg instead of the live one. Doing it before the
        // partner search below is safe: the search only inspects the OTHER
        // legs of the session.
        CallSession session = call.getSession();

        if (session != null) {

            session.removeLeg(call);

            // A session with no legs left is terminal (an empty leg list
            // derives ENDED and inactive), so evict it. The value-checked
            // remove avoids evicting a fresh session already recreated
            // under the same key.
            if (session.getLegs().isEmpty()) {

                sessionsByKey.remove(session.getKey(), session);
            }
        }

        // A peer-local call is one logical call shown as two legs (the
        // outgoing and incoming legs). Baresip does not always terminate
        // both legs together, so when one leg ends the partner leg must
        // end too or the local peer keeps a dangling card. The session
        // owns both legs, so we finish every other leg it contains.
        CallLeg partner = null;

        // Try to find the partner leg via the session first
        if (session != null) {
            for (CallLeg leg : session.getLegs()) {
                if (leg != call && leg.getState() != CallState.ENDED) {
                    partner = leg;
                    break;
                }
            }
        }

        // If not found in the session, search activeCalls as fallback.
        // We look for a leg belonging to a different account (the peer leg
        // of a peer-local call). This handles the case where sessionKey
        // normalization diverges between the outgoing and incoming legs
        // (e.g. due to forking or different username formats). The candidate
        // must correlate by identity, or the fallback could end an
        // unrelated active call on another account.
        if (partner == null && call.getAccount() != null) {
            for (CallLeg leg : activeCalls) {
                if (leg != call && leg.getState() != CallState.ENDED && leg.getAccount() != null
                    && !leg.getAccount().getId().equals(call.getAccount().getId()) && arePeersOfSameCall(call, leg)) {
                    partner = leg;
                    break;
                }
            }
        }

        if (partner != null) {

            JSoftSipLog.info("Ending partner leg of peer-local call " + call.getId());

            // Release the dangling baresip leg too, otherwise the
            // backend keeps the line alive and the account stays
            // busy even though the UI card is gone.
            sipClient.endCall(partner.getBackendCallId());

            // Recursive call stays under the monitor captured at
            // entry: reentrant for the shared-session primary path
            doFinishCall(partner, partner.getBackendCallId());
        }
    }

    /**
     * Identity correlation for the partner fallback: true when either
     * leg's destination peer designates the other leg's account after
     * SipPeer normalization. Guards the fallback so finishing one call
     * can never end an unrelated active call on another account.
     */
    private static boolean arePeersOfSameCall(CallLeg a, CallLeg b) {

        return designatesAccount(a.getDestination(), b.getAccount())
            || designatesAccount(b.getDestination(), a.getAccount());
    }

    private static boolean designatesAccount(String peerRef, SipAccount account) {

        return account != null && SipPeer.parse(peerRef).map(peer -> peer.matches(account)).orElse(false);
    }

    /**
     * Hook invoked at the beginning of
     * {@link #finishCall}. Does nothing by default.
     */
    protected void onFinishingCall(CallLeg call, String backendCallId) {
    }

    /**
     * Hook invoked at the end of {@link #finishCall}, after the
     * call has been removed from the active collections. Does nothing
     * by default.
     */
    protected void onCallFinished(CallLeg call) {
    }

    /**
     * Creates an incoming call from the given event, adds it to the
     * active collections and notifies the listeners.
     *
     * <p>Hooks:
     * <ul>
     *   <li>{@link #onIncomingCallStarted} is invoked before the
     *       account lookup (useful for logging).</li>
     *   <li>{@link #onIncomingCallCreated} is invoked after the
     *       call object is initialized but before it is added to the
     *       active collections (useful for backend-specific fields).</li>
     *   <li>{@link #scheduleIncomingCallTimeout} is invoked after
     *       the listeners are notified (useful for simulated
     *       timeouts).</li>
     * </ul>
     */
    protected final CallLeg createIncomingCall(CallEvent event) {

        onIncomingCallStarted(event);

        SipAccount account = accountService.findById(event.getAccountId()).orElseThrow(() -> new IllegalStateException(
            "Account not found: " + event.getAccountId()));

        CallLeg call = new CallLeg();

        call.setBackendCallId(event.getCallId());

        call.setAccount(account);

        call.setDirection(CallDirection.INCOMING);

        call.setDestination(event.getRemoteUri());

        onIncomingCallCreated(call, event);

        CallStateMachine.transition(CallState.IDLE, CallTransition.INCOMING_CALL).ifPresent(call::setState);

        // Registration runs under the session monitor so it cannot
        // interleave with a concurrent teardown of the same session.
        // addLeg is already synchronized internally but only covers
        // its own step, not the map registrations around it
        CallSession session = getOrCreateSession(CallSession.sessionKey(account, event.getRemoteUri()));

        synchronized (session) {

            attachNewLeg(call, event.getCallId(), session);
        }

        notifyListeners(call);

        scheduleIncomingCallTimeout(call, event);

        return call;
    }

    /**
     * Hook invoked at the beginning of
     * {@link #createIncomingCall}. Does nothing by default.
     */
    protected void onIncomingCallStarted(CallEvent event) {
    }

    /**
     * Hook invoked after the incoming call object is initialized
     * but before it is added to the active collections. Does nothing
     * by default.
     */
    protected void onIncomingCallCreated(CallLeg call, CallEvent event) {
    }

    /**
     * Hook invoked after the listeners are notified for a new
     * incoming call. Does nothing by default.
     */
    protected void scheduleIncomingCallTimeout(CallLeg call, CallEvent event) {
    }

    /**
     * Shared registration path for outgoing calls: builds the leg,
     * marks the intra-app condition from the
     * dial target, transitions IDLE to DIALING and attaches the leg
     * to its correlation session under the session monitor, so no
     * backend can bypass the aggregation and partner logic. Notifies
     * the listeners once the leg is fully registered.
     *
     * @return the registered leg, ready to be returned by
     *         {@link #startCall}
     */
    protected final CallLeg registerOutgoingCall(SipAccount account, String destination, String backendCallId) {

        CallLeg call = new CallLeg();

        call.setBackendCallId(backendCallId);

        call.setAccount(account);

        call.setDestination(destination);

        call.setPeerLocalAccount(SipPeer.isLocalAccount(accountService.getAccounts(), destination));

        call.setDirection(CallDirection.OUTGOING);

        CallStateMachine.transition(CallState.IDLE, CallTransition.START_DIAL).ifPresent(call::setState);

        // Registration runs under the session monitor so it cannot
        // interleave with a concurrent teardown of the same session.
        // addLeg is already synchronized internally but only covers
        // its own step, not the map registrations around it
        CallSession session = getOrCreateSession(CallSession.sessionKey(account, destination));

        synchronized (session) {

            attachNewLeg(call, backendCallId, session);
        }

        JSoftSipLog.info("Active calls: " + activeCalls.size());

        notifyListeners(call);

        return call;
    }

    /**
     * Attaches a freshly built leg to the active collections and
     * its session. The caller must hold the session monitor so the
     * whole registration sequence stays atomic against a concurrent
     * teardown of the same session.
     */
    private void attachNewLeg(CallLeg call, String backendCallId, CallSession session) {

        session.addLeg(call);

        activeCalls.add(call);

        callsByBackendId.put(backendCallId, call);
    }

    /**
     * Shared event-processing skeleton for every backend: resolves
     * the target leg, mints incoming calls for unknown ids and runs
     * the state-machine mapping plus the terminal decision under the
     * owning session monitor. Backend-specific bookkeeping lives in
     * {@link #applyCallEvent}, forked-INVITE filtering in
     * {@link #isForkedIncomingCall}.
     */
    protected final void processCallEvent(CallEvent event) {

        CallLeg call = callsByBackendId.get(event.getCallId());

        if (call == null) {

            if (event.getState() == SipCallState.INCOMING) {

                if (isForkedIncomingCall(event)) {

                    JSoftSipLog.info("Ignoring duplicate incoming call from " + event.getRemoteUri() + " on account "
                        + event.getAccountId());
                } else {

                    createIncomingCall(event);
                }
            }

            return;
        }

        boolean changed;

        // The mapping and the mutation it feeds read and write
        // the same state, so both run under the session monitor:
        // a concurrent hold or termination can no longer slip
        // between the check and the act
        synchronized (stateMonitorOf(call)) {

            Optional<CallState> next = CallStateMachine.transition(call.getState(),
                                                                   CallStateMachine.toTransition(call.getState(),
                                                                                                 event.getState(),
                                                                                                 event.isRemoteSendonly()));

            if (next.isPresent()) {

                applyCallEvent(call, event, next.get());

                if (next.get() == CallState.ENDED) {

                    finishCall(call, event.getCallId());

                } else {

                    call.setState(next.get());
                }

                changed = true;

            } else {

                changed = false;
            }
        }

        if (changed) {

            notifyListeners(call);
        }
    }

    /**
     * True when an incoming INVITE is a forked duplicate of a call
     * that already belongs to a session. Backends without forked
     * INVITEs keep the default: every unknown INCOMING id creates a
     * call.
     */
    protected boolean isForkedIncomingCall(CallEvent event) {

        return false;
    }

    /**
     * Backend-specific side effects of an accepted transition,
     * invoked inside the session monitor before the skeleton applies
     * the new state or finishes the leg. Must not mutate the leg
     * state itself: the skeleton owns that decision.
     */
    protected abstract void applyCallEvent(CallLeg call, CallEvent event, CallState next);
}
