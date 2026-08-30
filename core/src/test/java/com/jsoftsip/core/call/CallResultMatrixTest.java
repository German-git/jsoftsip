package com.jsoftsip.core.call;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Full matrix of the canonical result derivation: direction x
 * final state. Locks the contract that a CONNECTED or HELD call
 * is ANSWERED in BOTH directions - the old service-side
 * derivation fell back to CANCELLED for an answered incoming
 * call that terminated without an explicit result.
 */
class CallResultMatrixTest {

    @Test
    void incomingCallMissedWhenTerminatedWhileIncoming() {

        assertResult(CallDirection.INCOMING, CallState.INCOMING, CallResult.MISSED);
    }

    @Test
    void incomingCallCancelledWhileRinging() {

        assertResult(CallDirection.INCOMING, CallState.RINGING, CallResult.CANCELLED);
    }

    @Test
    void incomingCallCancelledWhileDialing() {

        assertResult(CallDirection.INCOMING, CallState.DIALING, CallResult.CANCELLED);
    }

    @Test
    void incomingCallAnsweredWhenConnected() {

        assertResult(CallDirection.INCOMING, CallState.CONNECTED, CallResult.ANSWERED);
    }

    @Test
    void incomingCallAnsweredWhenHeld() {

        assertResult(CallDirection.INCOMING, CallState.HOLD, CallResult.ANSWERED);
    }

    @Test
    void incomingCallFailedWhenTerminatedFromIdle() {

        assertResult(CallDirection.INCOMING, CallState.IDLE, CallResult.FAILED);
    }

    @Test
    void incomingCallFailedWhenTerminatedFromEnded() {

        assertResult(CallDirection.INCOMING, CallState.ENDED, CallResult.FAILED);
    }

    @Test
    void outgoingCallCancelledWhileRinging() {

        assertResult(CallDirection.OUTGOING, CallState.RINGING, CallResult.CANCELLED);
    }

    @Test
    void outgoingCallCancelledWhileDialing() {

        assertResult(CallDirection.OUTGOING, CallState.DIALING, CallResult.CANCELLED);
    }

    @Test
    void outgoingCallCancelledWhileIncoming() {

        assertResult(CallDirection.OUTGOING, CallState.INCOMING, CallResult.CANCELLED);
    }

    @Test
    void outgoingCallAnsweredWhenConnected() {

        assertResult(CallDirection.OUTGOING, CallState.CONNECTED, CallResult.ANSWERED);
    }

    @Test
    void outgoingCallAnsweredWhenHeld() {

        assertResult(CallDirection.OUTGOING, CallState.HOLD, CallResult.ANSWERED);
    }

    @Test
    void outgoingCallFailedWhenTerminatedFromIdle() {

        assertResult(CallDirection.OUTGOING, CallState.IDLE, CallResult.FAILED);
    }

    @Test
    void outgoingCallFailedWhenTerminatedFromEnded() {

        assertResult(CallDirection.OUTGOING, CallState.ENDED, CallResult.FAILED);
    }

    @Test
    void explicitResultWinsOverDerivation() {

        CallLeg call = new CallLeg();

        call.setDirection(CallDirection.OUTGOING);

        call.setState(CallState.INCOMING);

        call.setResult(CallResult.ANSWERED);

        assertEquals(CallResult.ANSWERED, call.resolveResult(),
                     "a result set by the live event flow must never be re-derived");
    }

    private static void assertResult(CallDirection direction, CallState state, CallResult expected) {

        CallLeg call = new CallLeg();

        call.setDirection(direction);

        call.setState(state);

        assertEquals(expected, call.resolveResult(), "cell " + direction + " x " + state);
    }
}