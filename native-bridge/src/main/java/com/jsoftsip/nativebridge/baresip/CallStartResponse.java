package com.jsoftsip.nativebridge.baresip;

public class CallStartResponse {

    private final boolean ok;

    private final String callId;

    private final String token;

    public CallStartResponse(boolean ok, String callId, String token) {

        this.ok = ok;

        this.callId = callId;

        this.token = token;
    }

    public boolean isOk() {
        return ok;
    }

    public String getCallId() {
        return callId;
    }

    /**
     * Request token echoed by baresip in the response,
     * used to correlate a response with the dial that
     * produced it. Null when the request carried no token.
     */
    public String getToken() {
        return token;
    }
}
