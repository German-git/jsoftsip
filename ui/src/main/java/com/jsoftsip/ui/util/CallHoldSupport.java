package com.jsoftsip.ui.util;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallState;

/**
 * JavaFX-free decision logic for the Hold button of the active
 * call card. Kept free of JavaFX imports so plain unit tests
 * reach it headless.
 *
 * Hold is unavailable for calls between accounts of this same
 * application: the baresip 4.6.0 backend flip-flops the two
 * local legs on hold/resume, so no command sequence leaves
 * both legs sendrecv. The button is rendered as disabled and
 * the reason is shown as the tooltip.
 */
public final class CallHoldSupport {

    public static final String PEER_LOCAL_REASON = "callcard.hold.disabled.tooltip";

    private CallHoldSupport() {
    }

    /**
     * True when Hold can be toggled for the given call: the
     * call must be connected (or on hold) and its peer must
     * not be another account of this same application.
     */
    public static boolean isHoldAllowed(CallLeg call) {
        if (call == null || call.isPeerLocalAccount()) {
            return false;
        }

        CallState state = call.getState();

        return state == CallState.CONNECTED || state == CallState.HOLD;
    }

    /**
     * Tooltip for the Hold button: the peer-local limitation
     * reason when Hold is disabled for an intra-app call, the
     * regular label otherwise.
     */
    public static String holdTooltip(CallLeg call, String regularLabel) {
        if (call != null && call.isPeerLocalAccount()) {
            return PEER_LOCAL_REASON;
        }

        return regularLabel;
    }
}