package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link CallSession} aggregate invariants, driven by
 * the call-session-management spec scenarios.
 */
class CallSessionTest {

    private static SipAccount account(String username, String domain) {
        SipAccount account = new SipAccount();
        account.setId(Math.abs((long) username.hashCode()));
        account.setUsername(username);
        account.setDomain(domain);
        return account;
    }

    @Test
    void sessionKeyIsSymmetricForOutgoingAndIncomingDirections() {
        // CSM-002: the correlation key must be order-independent so the
        // outgoing leg (A dials B) and the incoming leg (B, from A) map to
        // the same session.
        SipAccount caller = account("1003", "192.168.0.97");
        SipAccount callee = account("1002", "192.168.0.97");

        String outgoingKey = CallSession.sessionKey(caller, "1002");
        String incomingKey = CallSession.sessionKey(callee, "sip:1003@192.168.0.97");

        assertEquals(outgoingKey, incomingKey, "outgoing and incoming legs of one local call must share a session key");
    }

    @Test
    void sessionKeyIgnoresHostSoBareNumberAndHostedPeerCollide() {
        // CSM-002 / local-call grouping: a bare-number dial target has no
        // host while its incoming counterpart reports a host. The key must
        // ignore the host, otherwise one logical call would split across
        // two sessions.
        SipAccount caller = account("1003", "192.168.0.97");
        SipAccount callee = account("1002", "192.168.0.97");

        String bareKey = CallSession.sessionKey(caller, "1002");
        String hostedKey = CallSession.sessionKey(callee, "sip:1003@192.168.0.97");

        assertEquals(bareKey, hostedKey, "a bare-number dial and its hosted incoming peer must collide to one key");
    }

    @Test
    void deriveStateIsConnectedWhenAnyLegIsConnected() {
        // CSM-003: CONNECTED if any leg is CONNECTED (or HOLD).
        CallSession session = new CallSession("1002#1003");

        CallLeg outgoing = new CallLeg();
        outgoing.setState(CallState.DIALING);

        CallLeg incoming = new CallLeg();
        incoming.setState(CallState.RINGING);

        session.addLeg(outgoing);
        session.addLeg(incoming);

        assertEquals(CallState.IDLE, session.getState(), "no leg connected yet");
        assertTrue(session.isActive());

        outgoing.setState(CallState.CONNECTED);

        assertEquals(CallState.CONNECTED, session.getState());
        assertTrue(session.isActive());
    }

    @Test
    void deriveStateIsEndedOnlyWhenAllLegsEnded() {
        // CSM-003: ENDED when all legs are ENDED, otherwise stays active.
        CallSession session = new CallSession("1002#1003");

        CallLeg outgoing = new CallLeg();
        outgoing.setState(CallState.CONNECTED);

        CallLeg incoming = new CallLeg();
        incoming.setState(CallState.CONNECTED);

        session.addLeg(outgoing);
        session.addLeg(incoming);

        assertEquals(CallState.CONNECTED, session.getState());
        assertTrue(session.isActive());

        outgoing.setState(CallState.ENDED);

        assertEquals(CallState.CONNECTED, session.getState(), "one leg still connected");
        assertTrue(session.isActive());

        incoming.setState(CallState.ENDED);

        assertEquals(CallState.ENDED, session.getState());
        assertFalse(session.isActive(), "session inactive once every leg ended");
    }

    @Test
    void endPartnerOfEndsTheOtherLegLeavingNoDanglingPartner() {
        // CSM-008: ending one leg ends its partner, preserving the
        // baresip independent-leg-termination invariant.
        CallSession session = new CallSession("1002#1003");

        CallLeg outgoing = new CallLeg();
        outgoing.setState(CallState.CONNECTED);

        CallLeg incoming = new CallLeg();
        incoming.setState(CallState.CONNECTED);

        session.addLeg(outgoing);
        session.addLeg(incoming);

        // Simulate the outgoing leg ending: the aggregate must end its partner.
        outgoing.setState(CallState.ENDED);
        session.endPartnerOf(outgoing);

        assertEquals(CallState.ENDED, incoming.getState(), "partner leg must be ended");
        assertFalse(session.isActive(), "session inactive once every leg ended");
        assertEquals(CallState.ENDED, session.getState());
    }

    @Test
    void removeLegClearsTheLegBackref() {
        // A removed leg must stop pointing at the
        // evicted session. Otherwise a late mutation on the dead leg
        // (for example a stray setState) still re-derives and notifies
        // a session nobody observes anymore.
        CallSession session = new CallSession("1002#1003");

        CallLeg outgoing = new CallLeg();
        outgoing.setState(CallState.CONNECTED);

        session.addLeg(outgoing);

        session.removeLeg(outgoing);

        assertFalse(session.getLegs().contains(outgoing), "the leg is removed from the aggregate");

        assertNull(outgoing.getSession(), "removeLeg must clear the back reference");
    }

    @Test
    void sessionObserverReceivesEventWhenALegTransitions() {
        // CSM-004: observers receive a session-level change event when a
        // leg within the session transitions, without having to correlate
        // individual legs themselves.
        CallSession session = new CallSession("1002#1003");

        CallLeg outgoing = new CallLeg();
        outgoing.setState(CallState.DIALING);

        CallLeg incoming = new CallLeg();
        incoming.setState(CallState.RINGING);

        session.addLeg(outgoing);
        session.addLeg(incoming);

        boolean[] notified = {false};
        CallSessionListener listener = changed -> {
            notified[0] = true;
            assertEquals(session, changed, "observer receives the session, not a single leg");
        };
        session.addSessionListener(listener);

        // A transition on either leg must surface as a session-level event.
        outgoing.setState(CallState.CONNECTED);

        assertTrue(notified[0], "observer must be notified on a leg transition");
        assertEquals(CallState.CONNECTED, session.getState());
    }
}
