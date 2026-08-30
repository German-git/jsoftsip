package com.jsoftsip.core.sip;

public interface SipClient {

    void initialize();

    void shutdown();

    void registerAccount(SipAccountData account);

    void unregisterAccount(long accountId);

    String startCall(long accountId, String destination);

    void answerCall(String callId);

    void rejectCall(String callId);

    void endCall(String callId);

    void holdCall(String callId);

    void resumeCall(String callId);

    /**
     * Sets the audio output volume of the SIP transport,
     * in the range 0..100. Implementations that cannot
     * control the audio path must document the limitation
     * and still record the value so client state stays
     * consistent.
     */
    void setVolume(int volume);

    /**
     * Sets the microphone input volume of the SIP transport,
     * in the range 0..100. Implementations that cannot
     * control the audio path must document the limitation
     * and still record the value so client state stays
     * consistent.
     */
    void setMicrophoneVolume(int volume);

    /**
     * Mutes or unmutes the microphone input of the SIP
     * transport. Implementations that cannot control the
     * audio path must document the limitation and still
     * record the value so client state stays consistent.
     */
    void setMicrophoneMuted(boolean muted);

    void addRegistrationListener(SipEventListener listener);

    void addCallListener(SipCallListener listener);

    /**
     * Enables or disables video transmission (outgoing video)
     * for the active call. The default implementation is a
     * no-op that reports success: backends without video
     * support (e.g. MOCK) never break the UI toggle. Real
     * backends override this to send the appropriate control
     * command and return the actual result.
     *
     * @return true when the toggle was accepted or is a no-op,
     *         false when the command failed or the connection
     *         is unavailable
     */
    default boolean setVideoTransmissionEnabled(boolean enabled) {

        return true;
    }

    /**
     * Returns the normalized AOR (sip:user@domain) for the
     * given account id, or null if the account has no active
     * UA in the backend.
     */
    default String getAorForAccount(long accountId) {

        return null;
    }

    /**
     * Reverse lookup of {@link #getAorForAccount(long)}: returns
     * the account id that currently owns the given AOR, or null
     * when the AOR is unknown. Used by the video frame pipe to
     * route incoming frames to the correct account.
     */
    default Long accountIdForAor(String aor) {

        return null;
    }
}