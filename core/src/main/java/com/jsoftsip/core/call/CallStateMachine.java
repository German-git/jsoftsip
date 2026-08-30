package com.jsoftsip.core.call;

import com.jsoftsip.core.sip.SipCallState;

import java.util.Map;
import java.util.Optional;

/**
 * Pure immutable transition table for the call lifecycle.
 * Both call services drive every state change through this
 * table so the mapping stays in a single place.
 * <p>
 * The table is intentionally strict: only the transitions that the
 * services can legitimately exercise are allowed. Creation events
 * ({@code START_DIAL} and {@code INCOMING_CALL}) only originate from
 * {@code IDLE}, because every call is created in that state. Idempotent
 * re-asserts are kept for backend events that may be repeated (for example
 * {@code RINGING} while already in {@code RINGING}). All active states can
 * move to {@code ENDED} via {@code FAIL} or {@code TERMINATE}. {@code HOLD}
 * is only accepted from {@code CONNECTED} and from {@code HOLD} itself,
 * because holding a call that is not established is a no-op.
 * </p>
 */
public final class CallStateMachine {

    private record TransitionKey(CallState from, CallTransition event) {
    }

    private static final Map<TransitionKey, CallState> TABLE = Map.ofEntries(Map.entry(new TransitionKey(
        CallState.IDLE, CallTransition.START_DIAL), CallState.DIALING),
                                                                             Map.entry(new TransitionKey(CallState.IDLE,
                                                                                 CallTransition.INCOMING_CALL),
                                                                                       CallState.INCOMING),

                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.DIALING,
                                                                                 CallTransition.RINGING),
                                                                                       CallState.RINGING),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.DIALING,
                                                                                 CallTransition.CONNECT),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.DIALING,
                                                                                 CallTransition.FAIL), CallState.ENDED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.DIALING,
                                                                                 CallTransition.TERMINATE),
                                                                                       CallState.ENDED),

                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.INCOMING,
                                                                                 CallTransition.RINGING),
                                                                                       CallState.RINGING),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.INCOMING,
                                                                                 CallTransition.CONNECT),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.INCOMING,
                                                                                 CallTransition.FAIL), CallState.ENDED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.INCOMING,
                                                                                 CallTransition.TERMINATE),
                                                                                       CallState.ENDED),

                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.RINGING,
                                                                                 CallTransition.RINGING),
                                                                                       CallState.RINGING),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.RINGING,
                                                                                 CallTransition.CONNECT),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.RINGING,
                                                                                 CallTransition.FAIL), CallState.ENDED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.RINGING,
                                                                                 CallTransition.TERMINATE),
                                                                                       CallState.ENDED),

                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.CONNECTED,
                                                                                 CallTransition.CONNECT),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.CONNECTED,
                                                                                 CallTransition.HOLD), CallState.HOLD),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.CONNECTED,
                                                                                 CallTransition.RESUME),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.CONNECTED,
                                                                                 CallTransition.FAIL), CallState.ENDED),
                                                                             Map.entry(new TransitionKey(
                                                                                 CallState.CONNECTED,
                                                                                 CallTransition.TERMINATE),
                                                                                       CallState.ENDED),

                                                                             Map.entry(new TransitionKey(CallState.HOLD,
                                                                                 CallTransition.CONNECT),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(CallState.HOLD,
                                                                                 CallTransition.HOLD), CallState.HOLD),
                                                                             Map.entry(new TransitionKey(CallState.HOLD,
                                                                                 CallTransition.RESUME),
                                                                                       CallState.CONNECTED),
                                                                             Map.entry(new TransitionKey(CallState.HOLD,
                                                                                 CallTransition.FAIL), CallState.ENDED),
                                                                             Map.entry(new TransitionKey(CallState.HOLD,
                                                                                 CallTransition.TERMINATE),
                                                                                       CallState.ENDED));

    private CallStateMachine() {
    }

    /**
     * Returns the target state for the transition, or empty
     * when the transition is not allowed.
     */
    public static Optional<CallState> transition(CallState from, CallTransition event) {

        return Optional.ofNullable(TABLE.get(new TransitionKey(from, event)));
    }

    /**
     * Maps a backend call event onto the machine event that
     * reproduces the state change the services perform.
     */
    public static CallTransition toTransition(SipCallState state) {

        return switch (state) {

            case DIALING -> CallTransition.START_DIAL;

            case INCOMING -> CallTransition.INCOMING_CALL;

            case RINGING -> CallTransition.RINGING;

            case ESTABLISHED -> CallTransition.CONNECT;

            case HOLD -> CallTransition.HOLD;

            case FAILED -> CallTransition.FAIL;

            case TERMINATED -> CallTransition.TERMINATE;
        };
    }

    /**
     * SDP-aware refinement of {@link #toTransition(SipCallState)}.
     *
     * <p>An ESTABLISHED event whose remote audio direction is
     * sendonly carries a hold re-INVITE answer whenever the leg
     * is already active: from CONNECTED it is either the
     * confirmation of our own hold request or a remote hold, and
     * from HOLD it repeats that same answer. Both map onto HOLD,
     * because the plain CONNECT mapping would rebound the leg to
     * CONNECTED right after the hold. A sendrecv ESTABLISHED on
     * HOLD keeps the plain CONNECT mapping, which is exactly the
     * resume confirmation. Events on pre-connect legs also keep
     * the plain mapping, since early-media establishments
     * legitimately carry sendonly audio.
     */
    public static CallTransition toTransition(CallState current, SipCallState state, boolean remoteSendonly) {

        if (remoteSendonly && state == SipCallState.ESTABLISHED
            && (current == CallState.CONNECTED || current == CallState.HOLD)) {

            return CallTransition.HOLD;
        }

        return toTransition(state);
    }
}
