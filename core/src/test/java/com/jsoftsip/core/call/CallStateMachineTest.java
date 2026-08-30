package com.jsoftsip.core.call;

import com.jsoftsip.core.sip.SipCallState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the strict transition table that the call services exercise.
 * Only transitions that the services can legitimately trigger are
 * allowed, everything else returns empty.
 */
class CallStateMachineTest {

    @Test
    void idleAcceptsCreationEvents() {

        assertEquals(Optional.of(CallState.DIALING),
                     CallStateMachine.transition(CallState.IDLE, CallTransition.START_DIAL),
                     "startCall must move a fresh call to DIALING");

        assertEquals(Optional.of(CallState.INCOMING),
                     CallStateMachine.transition(CallState.IDLE, CallTransition.INCOMING_CALL),
                     "an incoming event must create the call in INCOMING");
    }

    @Test
    void idleRejectsEveryOtherEvent() {

        for (CallTransition event : CallTransition.values()) {

            if (event == CallTransition.START_DIAL || event == CallTransition.INCOMING_CALL) {

                continue;
            }

            assertTrue(CallStateMachine.transition(CallState.IDLE, event).isEmpty(), "IDLE must reject " + event);
        }
    }

    @Test
    void dialingLifecycle() {

        assertEquals(Optional.of(CallState.RINGING),
                     CallStateMachine.transition(CallState.DIALING, CallTransition.RINGING));

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.DIALING, CallTransition.CONNECT));

        assertEquals(Optional.of(CallState.ENDED), CallStateMachine.transition(CallState.DIALING, CallTransition.FAIL));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.DIALING, CallTransition.TERMINATE));
    }

    @Test
    void dialingRejectsCreationAndHoldEvents() {

        assertTrue(CallStateMachine.transition(CallState.DIALING, CallTransition.START_DIAL).isEmpty(),
                   "DIALING must reject START_DIAL");

        assertTrue(CallStateMachine.transition(CallState.DIALING, CallTransition.INCOMING_CALL).isEmpty(),
                   "DIALING must reject INCOMING_CALL");

        assertTrue(CallStateMachine.transition(CallState.DIALING, CallTransition.HOLD).isEmpty(),
                   "DIALING must reject HOLD");

        assertTrue(CallStateMachine.transition(CallState.DIALING, CallTransition.RESUME).isEmpty(),
                   "DIALING must reject RESUME");
    }

    @Test
    void incomingLifecycle() {

        assertEquals(Optional.of(CallState.RINGING),
                     CallStateMachine.transition(CallState.INCOMING, CallTransition.RINGING));

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.INCOMING, CallTransition.CONNECT));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.INCOMING, CallTransition.FAIL));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.INCOMING, CallTransition.TERMINATE));
    }

    @Test
    void incomingRejectsCreationAndHoldEvents() {

        assertTrue(CallStateMachine.transition(CallState.INCOMING, CallTransition.START_DIAL).isEmpty(),
                   "INCOMING must reject START_DIAL");

        assertTrue(CallStateMachine.transition(CallState.INCOMING, CallTransition.INCOMING_CALL).isEmpty(),
                   "INCOMING must reject INCOMING_CALL");

        assertTrue(CallStateMachine.transition(CallState.INCOMING, CallTransition.HOLD).isEmpty(),
                   "INCOMING must reject HOLD");

        assertTrue(CallStateMachine.transition(CallState.INCOMING, CallTransition.RESUME).isEmpty(),
                   "INCOMING must reject RESUME");
    }

    @Test
    void ringingLifecycle() {

        assertEquals(Optional.of(CallState.RINGING),
                     CallStateMachine.transition(CallState.RINGING, CallTransition.RINGING),
                     "RINGING must accept the idempotent RINGING re-assert");

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.RINGING, CallTransition.CONNECT));

        assertEquals(Optional.of(CallState.ENDED), CallStateMachine.transition(CallState.RINGING, CallTransition.FAIL));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.RINGING, CallTransition.TERMINATE));
    }

    @Test
    void ringingRejectsCreationAndHoldEvents() {

        assertTrue(CallStateMachine.transition(CallState.RINGING, CallTransition.START_DIAL).isEmpty(),
                   "RINGING must reject START_DIAL");

        assertTrue(CallStateMachine.transition(CallState.RINGING, CallTransition.INCOMING_CALL).isEmpty(),
                   "RINGING must reject INCOMING_CALL");

        assertTrue(CallStateMachine.transition(CallState.RINGING, CallTransition.HOLD).isEmpty(),
                   "RINGING must reject HOLD");

        assertTrue(CallStateMachine.transition(CallState.RINGING, CallTransition.RESUME).isEmpty(),
                   "RINGING must reject RESUME");
    }

    @Test
    void connectedAcceptsEstablishedHoldAndTermination() {

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.CONNECT),
                     "CONNECTED must accept the idempotent CONNECT re-assert");

        assertEquals(Optional.of(CallState.HOLD),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.HOLD));

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.RESUME),
                     "CONNECTED must accept the idempotent RESUME re-assert");

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.FAIL));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.TERMINATE));
    }

    @Test
    void connectedRejectsCreationEvents() {

        assertTrue(CallStateMachine.transition(CallState.CONNECTED, CallTransition.START_DIAL).isEmpty(),
                   "CONNECTED must reject START_DIAL");

        assertTrue(CallStateMachine.transition(CallState.CONNECTED, CallTransition.INCOMING_CALL).isEmpty(),
                   "CONNECTED must reject INCOMING_CALL");

        assertTrue(CallStateMachine.transition(CallState.CONNECTED, CallTransition.RINGING).isEmpty(),
                   "CONNECTED must reject RINGING");
    }

    @Test
    void holdAcceptsEstablishedResumeAndTermination() {

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.HOLD, CallTransition.CONNECT));

        assertEquals(Optional.of(CallState.HOLD), CallStateMachine.transition(CallState.HOLD, CallTransition.HOLD),
                     "HOLD must accept the idempotent HOLD re-assert");

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.HOLD, CallTransition.RESUME));

        assertEquals(Optional.of(CallState.ENDED), CallStateMachine.transition(CallState.HOLD, CallTransition.FAIL));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.HOLD, CallTransition.TERMINATE));
    }

    @Test
    void holdRejectsCreationAndRingingEvents() {

        assertTrue(CallStateMachine.transition(CallState.HOLD, CallTransition.START_DIAL).isEmpty(),
                   "HOLD must reject START_DIAL");

        assertTrue(CallStateMachine.transition(CallState.HOLD, CallTransition.INCOMING_CALL).isEmpty(),
                   "HOLD must reject INCOMING_CALL");

        assertTrue(CallStateMachine.transition(CallState.HOLD, CallTransition.RINGING).isEmpty(),
                   "HOLD must reject RINGING");
    }

    @Test
    void endedRejectsEveryEvent() {

        for (CallTransition event : CallTransition.values()) {

            assertTrue(CallStateMachine.transition(CallState.ENDED, event).isEmpty(), "ENDED must reject " + event);
        }
    }

    @Test
    void outgoingCallLifecycle() {

        assertEquals(Optional.of(CallState.DIALING),
                     CallStateMachine.transition(CallState.IDLE, CallTransition.START_DIAL));

        assertEquals(Optional.of(CallState.RINGING),
                     CallStateMachine.transition(CallState.DIALING, CallTransition.RINGING));

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.RINGING, CallTransition.CONNECT));

        assertEquals(Optional.of(CallState.HOLD),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.HOLD));

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.HOLD, CallTransition.RESUME));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.TERMINATE));
    }

    @Test
    void incomingCallLifecycle() {

        assertEquals(Optional.of(CallState.INCOMING),
                     CallStateMachine.transition(CallState.IDLE, CallTransition.INCOMING_CALL));

        assertEquals(Optional.of(CallState.CONNECTED),
                     CallStateMachine.transition(CallState.INCOMING, CallTransition.CONNECT));

        assertEquals(Optional.of(CallState.ENDED),
                     CallStateMachine.transition(CallState.CONNECTED, CallTransition.FAIL));
    }

    @Test
    void everySipCallStateMapsToATransition() {

        assertEquals(CallTransition.START_DIAL, CallStateMachine.toTransition(SipCallState.DIALING));

        assertEquals(CallTransition.INCOMING_CALL, CallStateMachine.toTransition(SipCallState.INCOMING));

        assertEquals(CallTransition.RINGING, CallStateMachine.toTransition(SipCallState.RINGING));

        assertEquals(CallTransition.CONNECT, CallStateMachine.toTransition(SipCallState.ESTABLISHED));

        assertEquals(CallTransition.HOLD, CallStateMachine.toTransition(SipCallState.HOLD));

        assertEquals(CallTransition.FAIL, CallStateMachine.toTransition(SipCallState.FAILED));

        assertEquals(CallTransition.TERMINATE, CallStateMachine.toTransition(SipCallState.TERMINATED));
    }

    @Test
    void sendonlyEstablishedOnActiveLegsMapsToHold() {

        assertEquals(CallTransition.HOLD,
                     CallStateMachine.toTransition(CallState.CONNECTED, SipCallState.ESTABLISHED, true),
                     "a sendonly established event on CONNECTED is the hold confirmation or a remote hold");

        assertEquals(CallTransition.HOLD, CallStateMachine.toTransition(CallState.HOLD, SipCallState.ESTABLISHED, true),
                     "a sendonly established event on HOLD repeats the same hold answer");
    }

    @Test
    void sendrecvEstablishedOnHoldMapsToConnect() {

        assertEquals(CallTransition.CONNECT,
                     CallStateMachine.toTransition(CallState.HOLD, SipCallState.ESTABLISHED, false),
                     "a sendrecv established event on HOLD is the resume confirmation");
    }

    @Test
    void preConnectAndPlainEventsKeepThePlainMapping() {

        assertEquals(CallTransition.CONNECT,
                     CallStateMachine.toTransition(CallState.INCOMING, SipCallState.ESTABLISHED, true),
                     "early-media establishments with sendonly audio must still connect a pre-connect leg");

        assertEquals(CallTransition.CONNECT,
                     CallStateMachine.toTransition(CallState.DIALING, SipCallState.ESTABLISHED, true),
                     "early-media establishments with sendonly audio must still connect a dialing leg");

        assertEquals(CallTransition.CONNECT,
                     CallStateMachine.toTransition(CallState.RINGING, SipCallState.ESTABLISHED, false),
                     "plain events must keep the plain state-only mapping");

        assertEquals(CallTransition.HOLD, CallStateMachine.toTransition(CallState.HOLD, SipCallState.HOLD, true),
                     "explicit HOLD events map to HOLD regardless of the audio direction");
    }
}
