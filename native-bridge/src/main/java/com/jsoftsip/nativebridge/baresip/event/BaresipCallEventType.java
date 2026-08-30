package com.jsoftsip.nativebridge.baresip.event;

public enum BaresipCallEventType {

    CALL_OUTGOING,

    CALL_INCOMING,

    CALL_RINGING,

    CALL_ESTABLISHED,

    CALL_CLOSED,

    CALL_LOCAL_SDP,

    CALL_REMOTE_SDP,

    /**
     * Baresip 4.6.0 call event observed but not actionable by
     * the app: the call flow works without it, so it drops
     * silently instead of producing the UNKNOWN warn.
     */
    CALL_ANSWERED,

    /**
     * Baresip 4.6.0 call event observed but not actionable by
     * the app: the call flow works without it, so it drops
     * silently instead of producing the UNKNOWN warn.
     */
    CALL_RTPESTAB,

    /**
     * Baresip 4.6.0 call event observed but not actionable by
     * the app: emitted periodically (roughly every 5s) while a
     * call is active, carrying RTCP statistics, so it drops
     * silently instead of producing the UNKNOWN warn.
     */
    CALL_RTCP,

    /**
     * Baresip 4.6.0 ua event (wire value CREATE) observed but
     * not actionable by the app: it drops silently instead of
     * producing the UNKNOWN warn.
     */
    UA_CREATE,

    /**
     * Baresip 4.6.0 ua event (wire value SHUTDOWN) observed but
     * not actionable by the app: it drops silently instead of
     * producing the UNKNOWN warn.
     */
    UA_SHUTDOWN,

    UNKNOWN
}