package com.jsoftsip.core.sip;

public enum SipCallState {
    DIALING, // starting an outgoing call
    INCOMING, // incoming call received

    RINGING, // remote ringing (outgoing) or local ringing (incoming)

    // Active states
    ESTABLISHED, // call in progress

    // Modification states
    HOLD, // call on hold

    // Final states
    TERMINATED, // normal termination
    FAILED // termination due to error
}