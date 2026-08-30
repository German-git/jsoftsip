package com.jsoftsip.core.sip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the SipClient contract for video transmission toggle.
 * The default and mock implementations MUST be no-ops that report
 * success, so the UI toggle never breaks on backends that cannot
 * drive a real camera (REQ-7).
 */
class SipClientContractTest {

    /**
     * Concrete SipClient that overrides every abstract method as a
     * no-op, leaving the default setVideoTransmissionEnabled to be
     * tested in isolation.
     */
    private static final SipClient NOOP = new SipClient() {

        @Override
        public void initialize() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void registerAccount(SipAccountData account) {
        }

        @Override
        public void unregisterAccount(long accountId) {
        }

        @Override
        public String startCall(long accountId, String destination) {

            return null;
        }

        @Override
        public void answerCall(String callId) {
        }

        @Override
        public void rejectCall(String callId) {
        }

        @Override
        public void endCall(String callId) {
        }

        @Override
        public void holdCall(String callId) {
        }

        @Override
        public void resumeCall(String callId) {
        }

        @Override
        public void setVolume(int volume) {
        }

        @Override
        public void setMicrophoneVolume(int volume) {
        }

        @Override
        public void setMicrophoneMuted(boolean muted) {
        }

        @Override
        public void addRegistrationListener(SipEventListener listener) {
        }

        @Override
        public void addCallListener(SipCallListener listener) {
        }
    };

    @Test
    void defaultSetVideoTransmissionEnabledReportsTrueWhenEnabling() {

        boolean result = NOOP.setVideoTransmissionEnabled(true);

        assertTrue(result, "the default no-op must report success when enabling");
    }

    @Test
    void defaultSetVideoTransmissionEnabledReportsTrueWhenDisabling() {

        boolean result = NOOP.setVideoTransmissionEnabled(false);

        assertTrue(result, "the default no-op must report success when disabling");
    }

    @Test
    void defaultSetVideoTransmissionEnabledIsIdempotentAndSideEffectFree() {

        assertTrue(NOOP.setVideoTransmissionEnabled(true));

        assertTrue(NOOP.setVideoTransmissionEnabled(false));

        assertTrue(NOOP.setVideoTransmissionEnabled(true));

        assertTrue(NOOP.setVideoTransmissionEnabled(false));
    }

    @Test
    void mockClientInheritsTheNoOpDefault() {

        com.jsoftsip.core.call.MockSipClient mockClient = new com.jsoftsip.core.call.MockSipClient();

        assertTrue(mockClient.setVideoTransmissionEnabled(true), "MockSipClient must inherit the no-op default");

        assertTrue(mockClient.setVideoTransmissionEnabled(false), "MockSipClient must report success when disabling");
    }
}
