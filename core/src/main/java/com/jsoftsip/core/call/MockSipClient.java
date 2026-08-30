package com.jsoftsip.core.call;

import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipRegistrationState;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link SipClient} transport stub used by the
 * mock backend. It carries NO timing or simulation logic:
 * state transitions are injected by {@link MockCallService},
 * which owns the simulation via its scheduled executor.
 */
public class MockSipClient implements SipClient {

    private final List<SipEventListener> registrationListeners = new CopyOnWriteArrayList<>();

    private final List<SipCallListener> callListeners = new CopyOnWriteArrayList<>();

    private volatile int volume = 100;

    private volatile int microphoneVolume = 100;

    private volatile boolean microphoneMuted = false;

    private volatile boolean videoTransmissionEnabled = false;

    @Override
    public void initialize() {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void registerAccount(SipAccountData account) {

        SipRegistrationEvent event = new SipRegistrationEvent(account.getId(), SipRegistrationState.REGISTERED,
            "Mock registration");

        registrationListeners.forEach(listener -> listener.onRegistrationEvent(event));
    }

    @Override
    public void unregisterAccount(long accountId) {

        SipRegistrationEvent event = new SipRegistrationEvent(accountId, SipRegistrationState.UNREGISTERED,
            "Mock unregistration");

        registrationListeners.forEach(listener -> listener.onRegistrationEvent(event));
    }

    @Override
    public String startCall(long accountId, String destination) {

        String callId = UUID.randomUUID().toString();

        notifyCallListeners(new CallEvent(callId, accountId, destination, SipCallState.DIALING));

        return callId;
    }

    /**
     * Injects an established-state transition for an outgoing
     * call, called by {@link MockCallService} after its
     * simulated connect delay elapses.
     */
    public void simulateEstablished(String callId, long accountId, String remoteUri) {

        notifyCallListeners(new CallEvent(callId, accountId, remoteUri, SipCallState.ESTABLISHED));
    }

    /**
     * Injects an incoming call, called by
     * {@link MockCallService} when its simulated
     * incoming-call timer fires.
     */
    public void simulateIncomingCall(long accountId, String remoteUri) {

        String callId = UUID.randomUUID().toString();

        JSoftSipLog.debug("Generating incoming call for account " + accountId);

        notifyCallListeners(new CallEvent(callId, accountId, remoteUri, SipCallState.INCOMING));
    }

    @Override
    public void answerCall(String callId) {

        notifyCallListeners(new CallEvent(callId, 0, "", SipCallState.ESTABLISHED));
    }

    @Override
    public void rejectCall(String callId) {

        notifyCallListeners(new CallEvent(callId, 0, "", SipCallState.TERMINATED));
    }

    @Override
    public void endCall(String callId) {

        notifyCallListeners(new CallEvent(callId, 0, "", SipCallState.TERMINATED));
    }

    @Override
    public void holdCall(String callId) {

        notifyCallListeners(new CallEvent(callId, 0, "", SipCallState.HOLD));
    }

    @Override
    public void resumeCall(String callId) {

        notifyCallListeners(new CallEvent(callId, 0, "", SipCallState.ESTABLISHED));
    }

    @Override
    public void setVolume(int volume) {

        this.volume = volume;

        JSoftSipLog.debug("Volume set to " + volume);
    }

    @Override
    public void setMicrophoneVolume(int volume) {

        this.microphoneVolume = volume;

        JSoftSipLog.debug("Mic volume set to " + volume);
    }

    @Override
    public void setMicrophoneMuted(boolean muted) {

        this.microphoneMuted = muted;

        JSoftSipLog.debug("Mic muted: " + muted);
    }

    public boolean isMicrophoneMuted() {

        return microphoneMuted;
    }

    @Override
    public boolean setVideoTransmissionEnabled(boolean enabled) {

        this.videoTransmissionEnabled = enabled;

        JSoftSipLog.debug("Video TX " + (enabled ? "enabled" : "disabled"));

        return true;
    }

    public boolean isVideoTransmissionEnabled() {

        return videoTransmissionEnabled;
    }

    @Override
    public void addRegistrationListener(SipEventListener listener) {

        registrationListeners.add(listener);
    }

    @Override
    public void addCallListener(SipCallListener listener) {

        callListeners.add(listener);
    }

    private void notifyCallListeners(CallEvent event) {

        callListeners.forEach(listener -> listener.onCallEvent(event));
    }
}
