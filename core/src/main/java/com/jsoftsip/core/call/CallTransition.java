package com.jsoftsip.core.call;

/**
 * Events of the call state machine.
 * Each value corresponds to a code path that changes
 * the call state in the call services.
 */
public enum CallTransition {

    START_DIAL,

    INCOMING_CALL,

    RINGING,

    CONNECT,

    RESUME,

    HOLD,

    FAIL,

    TERMINATE
}
