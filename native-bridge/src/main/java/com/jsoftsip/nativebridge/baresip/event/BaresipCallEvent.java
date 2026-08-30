package com.jsoftsip.nativebridge.baresip.event;

public class BaresipCallEvent {

    private final String callId;

    private final String accountAor;

    private final String peerUri;

    private final String direction;

    private final BaresipCallEventType type;

    private final String param;

    private final String remoteAudioDir;

    public BaresipCallEvent(String callId, String accountAor, String peerUri, String direction,
                            BaresipCallEventType type, String param) {
        this(callId, accountAor, peerUri, direction, type, param, null);
    }

    public BaresipCallEvent(String callId, String accountAor, String peerUri, String direction,
                            BaresipCallEventType type, String param, String remoteAudioDir) {

        this.callId = callId;

        this.accountAor = accountAor;

        this.peerUri = peerUri;

        this.direction = direction;

        this.type = type;

        this.param = param;

        this.remoteAudioDir = remoteAudioDir;
    }

    public String getCallId() {
        return callId;
    }

    public String getAccountAor() {
        return accountAor;
    }

    public String getPeerUri() {
        return peerUri;
    }

    public String getDirection() {
        return direction;
    }

    public BaresipCallEventType getType() {
        return type;
    }

    public String getParam() {
        return param;
    }

    public String getRemoteAudioDir() {
        return remoteAudioDir;
    }
}