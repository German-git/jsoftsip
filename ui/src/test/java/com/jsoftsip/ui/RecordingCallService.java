package com.jsoftsip.ui;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallListener;
import com.jsoftsip.core.call.CallService;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared recording {@link CallService} stub for ui tests:
 * replaces the per-test hand-rolled stubs
 * by one implementation that records volume, mute and end-call
 * interactions while keeping the unsupported operations loud.
 * No Mockito dependency, matching the project's test culture.
 */
public class RecordingCallService implements CallService {

    private final List<CallLeg> activeCalls = new CopyOnWriteArrayList<>();

    private final List<Integer> outputVolumes = new CopyOnWriteArrayList<>();

    private final List<Integer> microphoneVolumes = new CopyOnWriteArrayList<>();

    private final List<Boolean> muteStates = new CopyOnWriteArrayList<>();

    private final List<String> endedCalls = new CopyOnWriteArrayList<>();

    /**
     * Replaces the seeded active-call list, tolerating null legs
     * exactly like the production lists they imitate.
     */
    public void seedActiveCalls(Collection<? extends CallLeg> legs) {

        activeCalls.clear();

        activeCalls.addAll(legs);
    }

    public void addActiveCall(CallLeg leg) {

        activeCalls.add(leg);
    }

    public List<Integer> outputVolumes() {

        return List.copyOf(outputVolumes);
    }

    public List<Integer> microphoneVolumes() {

        return List.copyOf(microphoneVolumes);
    }

    public List<Boolean> muteStates() {

        return List.copyOf(muteStates);
    }

    public List<String> endedCalls() {

        return List.copyOf(endedCalls);
    }

    /**
     * Bounded poll for tests that must wait until the n-th volume
     * command landed, replacing hand-rolled latches inside stubs.
     */
    public boolean awaitOutputVolumeCount(int minCount, long timeoutMillis) throws InterruptedException {

        long deadline = System.nanoTime() + timeoutMillis * 1_000_000;

        while (outputVolumes.size() < minCount && System.nanoTime() < deadline) {

            Thread.sleep(10);
        }

        return outputVolumes.size() >= minCount;
    }

    @Override
    public List<CallLeg> getActiveCalls() {

        // ArrayList copy, not List.copyOf: a null entry in the
        // active-call list is a real scenario ShutdownCleanup
        // guards against, and immutable copies reject nulls.
        return new java.util.ArrayList<>(activeCalls);
    }

    @Override
    public void setVolume(int volume) {

        outputVolumes.add(volume);
    }

    @Override
    public void setMicrophoneVolume(int volume) {

        microphoneVolumes.add(volume);
    }

    @Override
    public void setMicrophoneMuted(boolean muted) {

        muteStates.add(muted);
    }

    @Override
    public void endCall(String callId) {

        endedCalls.add(callId);
    }

    // Operations outside the recording scenarios stay loud so a
    // test relying on them fails instead of silently misbehaving.

    @Override
    public CallLeg startCall(SipAccount account, String destination) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void holdCall(String callId) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void resumeCall(String callId) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void answerCall(String callId) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void rejectCall(String callId) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void addListener(CallListener listener) {

        throw new UnsupportedOperationException();
    }

    @Override
    public void removeListener(CallListener listener) {

        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isVideoSupported() {

        // Matches the mock-backend reality where no video pipeline
        // exists, tests needing video support provide their own stub.
        return false;
    }

    @Override
    public boolean setVideoTransmissionEnabled(boolean enabled) {

        throw new UnsupportedOperationException();
    }
}
