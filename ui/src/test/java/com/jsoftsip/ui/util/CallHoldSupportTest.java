package com.jsoftsip.ui.util;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallState;
import com.jsoftsip.ui.I18n;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Hold button decision for the active call card:
 * state-based availability is kept intact, and calls between
 * accounts of this same application (peer-local) must never
 * offer Hold, with the tooltip explaining the backend
 * limitation instead of the regular label.
 */
class CallHoldSupportTest {

    private static CallLeg callIn(CallState state) {
        CallLeg call = new CallLeg();
        call.setState(state);
        return call;
    }

    @Test
    void holdAllowedWhenConnectedAndPeerIsExternal() {

        CallLeg call = callIn(CallState.CONNECTED);

        assertTrue(CallHoldSupport.isHoldAllowed(call));

        assertEquals("Hold CallLeg", CallHoldSupport.holdTooltip(call, "Hold CallLeg"));
    }

    @Test
    void holdAllowedWhenOnHoldAndPeerIsExternal() {

        assertTrue(CallHoldSupport.isHoldAllowed(callIn(CallState.HOLD)));
    }

    @Test
    void holdDisabledForIntraAppCall() {

        CallLeg call = callIn(CallState.CONNECTED);

        call.setPeerLocalAccount(true);

        assertFalse(CallHoldSupport.isHoldAllowed(call),
                    "hold must be disabled when the peer is another" + " account of this app");

        assertEquals(CallHoldSupport.PEER_LOCAL_REASON, CallHoldSupport.holdTooltip(call, "Hold CallLeg"),
                     "the tooltip must explain the limitation");
    }

    @Test
    void holdDisabledForIntraAppCallEvenInHoldState() {

        CallLeg call = callIn(CallState.HOLD);

        call.setPeerLocalAccount(true);

        assertFalse(CallHoldSupport.isHoldAllowed(call),
                    "hold must stay disabled in every state for" + " intra-app calls");

        assertEquals(CallHoldSupport.PEER_LOCAL_REASON, CallHoldSupport.holdTooltip(call, "Resume CallLeg"));
    }

    @Test
    void holdDisabledByStateBeforeConnected() {

        assertFalse(CallHoldSupport.isHoldAllowed(callIn(CallState.DIALING)));

        assertFalse(CallHoldSupport.isHoldAllowed(callIn(CallState.RINGING)));

        assertFalse(CallHoldSupport.isHoldAllowed(callIn(CallState.INCOMING)));
    }

    @Test
    void holdDisabledForNullCall() {

        assertFalse(CallHoldSupport.isHoldAllowed(null));

        assertEquals("Hold CallLeg", CallHoldSupport.holdTooltip(null, "Hold CallLeg"));
    }

    @Test
    void peerLocalReasonPinsTheLimitationCopy() {

        String reason = I18n.get(CallHoldSupport.PEER_LOCAL_REASON);

        assertTrue(reason.contains("unavailable"), "the tooltip must state that Hold is not" + " available");

        assertTrue(reason.contains("this app"), "the tooltip must explain the scope of the" + " limitation");
    }
}