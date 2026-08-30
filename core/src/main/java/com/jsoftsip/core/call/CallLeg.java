package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A leg of a logical call, owned by a {@link CallSession}.
 *
 * <p>It is the call model used across the application, extended with a back
 * reference to the session that aggregates the legs of one logical call
 * (for example the outgoing and incoming legs of a single local call between
 * two accounts of this application).
 *
 * <p>The {@code peerLocalAccount} flag is set by the call service exactly as
 * before, so the existing peer-local detection behaviour is preserved.
 */
public class CallLeg {

    private final String id = UUID.randomUUID().toString();

    private volatile SipAccount account;

    private volatile CallDirection direction;

    private volatile CallState state;

    private volatile CallResult result;

    private volatile LocalDateTime startedAt;

    private volatile LocalDateTime endedAt;

    private volatile String destination;

    private volatile String backendCallId;

    private volatile boolean peerLocalAccount;

    // Volatile because setState dereferences it from any thread
    // (backend reader, UI executor): without it a reader could see
    // null or a not-yet-published session
    private volatile CallSession session;

    /**
     * Associates this leg with the session that owns it. Called by
     * {@link CallSession#addLeg} so a leg always knows its parent,
     * and cleared again by {@link CallSession#removeLeg} when the
     * leg leaves the aggregate.
     */
    public void setSession(CallSession session) {
        this.session = session;
    }

    /**
     * Returns the owning session, or {@code null} when this leg has
     * not been attached to a session yet.
     */
    public CallSession getSession() {
        return session;
    }

    public void setState(CallState state) {
        this.state = state;

        // The owning session folds leg states into its own derived
        // state, so every transition must re-derive it. This keeps
        // activeProperty in sync for any caller that mutates a leg
        // (onCallEvent, hold/resume, finish) without each site having
        // to remember to notify the session.
        CallSession owner = getSession();

        if (owner != null) {
            owner.deriveState();
        }
    }

    public String getId() {
        return id;
    }

    public SipAccount getAccount() {
        return account;
    }

    public void setAccount(SipAccount account) {
        this.account = account;
    }

    public String getBackendCallId() {
        return backendCallId;
    }

    public void setBackendCallId(String backendCallId) {
        this.backendCallId = backendCallId;
    }

    /**
     * True when the call peer is another account registered
     * in this same application. Computed by the call service
     * when the peer is known.
     */
    public boolean isPeerLocalAccount() {
        return peerLocalAccount;
    }

    public void setPeerLocalAccount(boolean peerLocalAccount) {
        this.peerLocalAccount = peerLocalAccount;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public CallDirection getDirection() {
        return direction;
    }

    public void setDirection(CallDirection direction) {
        this.direction = direction;
    }

    public CallState getState() {
        return state;
    }

    public CallResult getResult() {
        return result;
    }

    public void setResult(CallResult result) {
        this.result = result;
    }

    /**
     * Returns the result, or derives a coherent one from the
     * direction/state the call ended in, mirroring the semantics
     * of {@code DefaultCallService.onCallEvent}. Never returns null.
     */
    public CallResult resolveResult() {

        if (result != null) {
            return result;
        }

        if (direction == CallDirection.INCOMING && state == CallState.INCOMING) {
            return CallResult.MISSED;
        }

        if (state == CallState.CONNECTED || state == CallState.HOLD) {
            return CallResult.ANSWERED;
        }

        if (state == CallState.DIALING || state == CallState.RINGING || state == CallState.INCOMING) {
            return CallResult.CANCELLED;
        }

        return CallResult.FAILED;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    @Override
    public String toString() {

        return String.format("%s -> %s [%s]", account != null ? account.getUsername() : "unknown", destination, state);
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationSeconds() {

        if (startedAt == null) {
            return 0;
        }

        LocalDateTime endTime = endedAt != null ? endedAt : LocalDateTime.now();

        return java.time.Duration.between(startedAt, endTime).getSeconds();
    }
}
