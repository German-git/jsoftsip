package com.jsoftsip.core.sip;

public class CallEvent implements SipEvent {

    private final String callId;

    private final long accountId;

    private final String remoteUri;

    private final SipCallState state;

    private final boolean remoteSendonly;

    public CallEvent(String callId, long accountId, String remoteUri, SipCallState state) {
        this(callId, accountId, remoteUri, state, false);
    }

    public CallEvent(String callId, long accountId, String remoteUri, SipCallState state, boolean remoteSendonly) {
        this.callId = callId;
        this.accountId = accountId;
        this.remoteUri = remoteUri;
        this.state = state;
        this.remoteSendonly = remoteSendonly;
    }

    public String getCallId() {
        return callId;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getRemoteUri() {
        return remoteUri;
    }

    public SipCallState getState() {
        return state;
    }

    /**
     * True when the remote offered only send-only audio
     * (remoteaudiodir=sendonly), which is what a hold
     * confirmation re-INVITE carries in its SDP answer.
     */
    public boolean isRemoteSendonly() {
        return remoteSendonly;
    }
}