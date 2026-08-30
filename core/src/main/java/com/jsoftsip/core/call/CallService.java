package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;

import java.util.List;

public interface CallService {

    CallLeg startCall(SipAccount account, String destination);

    void endCall(String callId);

    void holdCall(String callId);

    void resumeCall(String callId);

    List<CallLeg> getActiveCalls();

    void addListener(CallListener listener);

    void removeListener(CallListener listener);

    void answerCall(String callId);

    void rejectCall(String callId);

    /**
     * Sets the audio output volume of the active calls,
     * in the range 0..100.
     */
    void setVolume(int volume);

    /**
     * Sets the microphone input volume of the active calls,
     * in the range 0..100.
     */
    void setMicrophoneVolume(int volume);

    /**
     * Mutes or unmutes the microphone input of the active
     * calls.
     */
    void setMicrophoneMuted(boolean muted);

    /**
     * Returns true when the backend supports video transmission.
     * The UI uses this to decide whether to show the video toggle.
     */
    boolean isVideoSupported();

    /**
     * Enables or disables outgoing video transmission for the
     * active call. Backends without video support return true as
     * a no-op so the UI toggle never breaks.
     *
     * @return true when the toggle was accepted or is a no-op,
     *         false when the command failed
     */
    boolean setVideoTransmissionEnabled(boolean enabled);
}