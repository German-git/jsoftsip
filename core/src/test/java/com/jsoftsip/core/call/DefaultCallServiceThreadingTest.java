package com.jsoftsip.core.call;

import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipEventListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency contract of the call service state mutations: every
 * check-then-act sequence must serialize through the owning session
 * monitor so a backend TERMINATED event can never interleave with a
 * concurrent UI action. Covers the cases where a leg could
 * flip ENDED back to HOLD and where two racing terminations could
 * register the same call in the history twice.
 */
class DefaultCallServiceThreadingTest {

    private static final String PEER = "1001";

    /**
     * How long the gated backend commands stay blocked. Long enough
     * for the main thread to land its competing event, short enough
     * to keep the suite fast.
     */
    private static final long GATE_MILLIS = 75;

    @Test
    void terminatedEventWinsRaceAgainstHoldAndCallStaysEnded() throws Exception {

        GatedSipClient sipClient = new GatedSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new CallServiceTestFixtures.InMemoryAccounts(),
            new CallServiceTestFixtures.NoOpHistoryService());

        SessionSyncedListener listener = new SessionSyncedListener();

        service.addListener(listener);

        CallLeg call = service.startCall(CallServiceTestFixtures.account(1L, PEER), "1002");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED));

        assertEquals(CallState.CONNECTED, call.getState(), "the established event must connect the call");

        CountDownLatch holdCommandSent = new CountDownLatch(1);

        sipClient.gateHoldCalls(holdCommandSent);

        Thread holder = new Thread(() ->

        service.holdCall(call.getBackendCallId()), "hold-racer");

        holder.start();

        // The fake counts the latch down inside the backend hold
        // command, so from here the hold racer owns the session
        // monitor while blocked inside the command: the competing
        // termination must serialize behind it instead of
        // interleaving with the in-flight request
        assertTrue(holdCommandSent.await(5, TimeUnit.SECONDS), "the racer must reach the backend hold command");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.TERMINATED));

        holder.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(holder.isAlive(), "the hold racer must finish");

        assertEquals(CallState.ENDED, call.getState(),
                     "a delivered termination must win the race against hold and stay terminal");

        assertEquals(CallResult.ANSWERED, call.getResult(), "the result of the answered call must stay stable");

        listener.assertNeverObservedNonTerminalAfterEnded(call);
    }

    @Test
    void doubleTerminationRegistersHistoryExactlyOnce() throws Exception {

        GatedSipClient sipClient = new GatedSipClient(false);

        CountingHistoryService history = new CountingHistoryService();

        // The rendezvous pins both terminations past the ENDED guard
        // of finishCall before either one flips the state, which is
        // exactly the interleaving that triggered the original defect. Under the fix
        // the second termination waits on the session monitor instead,
        // never reaches the rendezvous in time and takes the solo path
        CyclicBarrier insideFinishCall = new CyclicBarrier(2);

        DefaultCallService service = new DefaultCallService(sipClient, new CallServiceTestFixtures.InMemoryAccounts(),
            history) {

            @Override
            protected void onFinishingCall(CallLeg call, String backendCallId) {

                try {

                    insideFinishCall.await(250, TimeUnit.MILLISECONDS);

                } catch (Exception soloPath) {

                    // Timed out or interrupted: proceed alone
                }
            }
        };

        CallLeg call = service.startCall(CallServiceTestFixtures.account(1L, PEER), "5551234");

        CountDownLatch release = new CountDownLatch(1);

        Runnable terminate = () -> {

            try {

                release.await(5, TimeUnit.SECONDS);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
            }

            sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "5551234", SipCallState.TERMINATED));
        };

        Thread first = new Thread(terminate, "terminator-a");

        Thread second = new Thread(terminate, "terminator-b");

        first.start();

        second.start();

        release.countDown();

        first.join(TimeUnit.SECONDS.toMillis(5));

        second.join(TimeUnit.SECONDS.toSeconds(5));

        assertFalse(first.isAlive() || second.isAlive(), "both terminators must finish");

        assertEquals(CallState.ENDED, call.getState(), "the call must end terminal");

        assertEquals(1, history.registrationCount(),
                     "racing terminations of the same call must register history exactly once");
    }

    @Test
    void endedRemainsTerminalUnderContendingHoldResumeAndTerminateThreads() throws Exception {

        int rounds = 40;

        int workersPerSide = 3;

        for (int round = 0; round < rounds; round++) {

            GatedSipClient sipClient = new GatedSipClient(false);

            DefaultCallService service = new DefaultCallService(sipClient,
                new CallServiceTestFixtures.InMemoryAccounts(), new CallServiceTestFixtures.NoOpHistoryService());

            SessionSyncedListener listener = new SessionSyncedListener();

            service.addListener(listener);

            CallLeg call = service.startCall(CallServiceTestFixtures.account(1L, PEER), "1002");

            sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1002", SipCallState.ESTABLISHED));

            String callId = call.getBackendCallId();

            CyclicBarrier barrier = new CyclicBarrier(workersPerSide * 2);

            List<Thread> workers = new ArrayList<>();

            for (int i = 0; i < workersPerSide; i++) {

                workers.add(new Thread(() -> {

                    await(barrier);

                    for (int cycle = 0; cycle < 25; cycle++) {

                        service.holdCall(callId);

                        service.resumeCall(callId);
                    }
                }, "hold-resume-" + round + "-" + i));

                workers.add(new Thread(() -> {

                    await(barrier);

                    sipClient.injectEvent(new CallEvent(callId, 1L, "1002", SipCallState.TERMINATED));
                }, "terminator-" + round + "-" + i));
            }

            for (Thread worker : workers) {

                worker.start();
            }

            for (Thread worker : workers) {

                worker.join(TimeUnit.SECONDS.toMillis(10));

                assertFalse(worker.isAlive(), "every worker must finish");
            }

            assertEquals(CallState.ENDED, call.getState(),
                         "round " + round + ": the call must end terminal under contention");

            assertEquals(CallResult.ANSWERED, call.getResult(), "round " + round + ": the result must stay stable");

            listener.assertNeverObservedNonTerminalAfterEnded(call);
        }
    }

    private static void await(CyclicBarrier barrier) {

        try {

            barrier.await(5, TimeUnit.SECONDS);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } catch (BrokenBarrierException | java.util.concurrent.TimeoutException e) {

            throw new IllegalStateException("workers failed to line up", e);
        }
    }

    /**
     * Transport fake whose hold and end commands can block on a latch
     * so the test pins a competing event into the middle of the
     * service lifecycle at a known point.
     */
    private static final class GatedSipClient implements SipClient {

        private final AtomicInteger nextCallId = new AtomicInteger();

        private final boolean gateHoldCommands;

        private volatile CountDownLatch holdGate;

        private volatile CountDownLatch endGate;

        private SipCallListener callListener;

        private GatedSipClient() {

            this(true);
        }

        private GatedSipClient(boolean gateHoldCommands) {

            this.gateHoldCommands = gateHoldCommands;
        }

        void gateHoldCalls(CountDownLatch gate) {

            this.holdGate = gate;
        }

        void gateEndCalls(CountDownLatch gate) {

            this.endGate = gate;
        }

        void injectEvent(CallEvent event) {

            callListener.onCallEvent(event);
        }

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

            return "call-" + nextCallId.incrementAndGet();
        }

        @Override
        public void answerCall(String callId) {
        }

        @Override
        public void rejectCall(String callId) {
        }

        @Override
        public void endCall(String callId) {

            openAndWait(endGate);
        }

        @Override
        public void holdCall(String callId) {

            if (gateHoldCommands) {

                openAndWait(holdGate);
            }
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

            this.callListener = listener;
        }

        private static void openAndWait(CountDownLatch gate) {

            if (gate == null) {

                return;
            }

            gate.countDown();

            try {

                Thread.sleep(GATE_MILLIS);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * History fake that counts how often the service recorded a
     * finished call.
     */
    private static final class CountingHistoryService implements HistoryService {

        private final AtomicInteger registrations = new AtomicInteger();

        int registrationCount() {

            return registrations.get();
        }

        @Override
        public void registerFinishedCall(CallLeg call) {

            registrations.incrementAndGet();
        }

        @Override
        public List<CallHistoryEntry> getHistory() {

            return List.of();
        }

        @Override
        public void clearAll() {
        }

        @Override
        public void addListener(Runnable listener) {
        }
    }

    /**
     * Listener fake that records every observed state. Appends happen
     * under the owning session monitor so the recorded sequence
     * reflects the order in which the service committed its critical
     * sections instead of the scheduling order of notifications fired
     * outside them.
     */
    private static final class SessionSyncedListener implements com.jsoftsip.core.call.CallListener {

        private final List<CallState> observed = new ArrayList<>();

        @Override
        public void onCallChanged(CallLeg call) {

            Object monitor = call.getSession() != null ? call.getSession() : call;

            synchronized (monitor) {

                observed.add(call.getState());
            }
        }

        void assertNeverObservedNonTerminalAfterEnded(CallLeg call) {

            List<CallState> snapshot;

            Object monitor = call.getSession() != null ? call.getSession() : call;

            synchronized (monitor) {

                snapshot = new ArrayList<>(observed);
            }

            boolean endedSeen = false;

            for (CallState state : snapshot) {

                if (endedSeen && state != CallState.ENDED) {

                    throw new AssertionError("listener observed " + state + " after ENDED, sequence was " + snapshot);
                }

                endedSeen = endedSeen || state == CallState.ENDED;
            }
        }
    }
}
