package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipRegistrationState;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Mock {@link CallService} used by the mock backend. It owns
 * the whole call simulation, driven by its
 * {@link ScheduledExecutorService}: outgoing calls connect
 * after a delay, incoming calls arrive shortly after an
 * account registers, and unanswered incoming calls time out.
 * The underlying {@link MockSipClient} is a thin transport
 * stub that only relays state transitions.
 */
public class MockCallService extends AbstractCallService implements SipCallListener, SipEventListener {

    private static final int OUTGOING_CONNECT_DELAY_SECONDS = 2;

    private static final int INCOMING_CALL_DELAY_SECONDS = 5;

    private static final int INCOMING_CALL_TIMEOUT_SECONDS = 15;

    private static final String MOCK_REMOTE_URI = "1001@sip.local";

    private final MockSipClient mockSipClient;

    private final ScheduledExecutorService simulationExecutor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());

    private static ThreadFactory daemonThreadFactory() {

        return runnable -> {

            Thread thread = new Thread(runnable, "jsoftsip-mock-simulation");

            thread.setDaemon(true);

            return thread;
        };
    }

    public MockCallService(MockSipClient sipClient, AccountService accountService, HistoryService historyService) {

        super(sipClient, accountService, historyService);

        this.mockSipClient = sipClient;

        sipClient.addCallListener(this);

        sipClient.addRegistrationListener(this);
    }

    @Override
    public CallLeg startCall(SipAccount account, String destination) {

        String backendCallId = sipClient.startCall(account.getId(), destination);

        // Same shared registration path as DefaultCallService: the
        // leg joins its correlation session and carries the
        // peer-local flag, so the MOCK backend exercises the same
        // aggregation and partner logic as the real one
        CallLeg call = registerOutgoingCall(account, destination, backendCallId);

        simulationExecutor.schedule(() -> mockSipClient.simulateEstablished(backendCallId, account.getId(),
                                                                            destination),
                                    OUTGOING_CONNECT_DELAY_SECONDS, TimeUnit.SECONDS);

        return call;
    }

    @Override
    public void holdCall(String callId) {

        // Same passthrough shape as DefaultCallService: the
        // command is forwarded under the session monitor and the
        // service performs no optimistic transition and no
        // notification of its own. State changes arrive through
        // onCallEvent, mirroring the real backend contract
        findCall(callId).ifPresent(call -> {

            synchronized (stateMonitorOf(call)) {

                sipClient.holdCall(call.getBackendCallId());
            }
        });
    }

    @Override
    public void resumeCall(String callId) {

        findCall(callId).ifPresent(call -> {

            synchronized (stateMonitorOf(call)) {

                sipClient.resumeCall(call.getBackendCallId());
            }
        });
    }

    @Override
    public void onRegistrationEvent(SipRegistrationEvent event) {

        if (event.getState() == SipRegistrationState.REGISTERED) {

            simulationExecutor.schedule(() -> mockSipClient.simulateIncomingCall(event.getAccountId(), MOCK_REMOTE_URI),
                                        INCOMING_CALL_DELAY_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onCallEvent(CallEvent event) {

        // Same event-driven policy as DefaultCallService: the
        // SDP-aware mapping decides the transition, so a sendonly
        // ESTABLISHED confirms or repeats a hold instead of
        // rebounding the leg to CONNECTED, and a sendrecv one
        // confirms the resume. Nothing here mutates state outside
        // what the machine accepts.
        processCallEvent(event);
    }

    /**
     * Mock-specific side effects: an establishment that connects a
     * non-held leg marks the call as answered and starts its
     * duration clock. The backend events carry no peer URI
     * refinement because the mock destination is already exact at
     * dial time.
     */
    @Override
    protected void applyCallEvent(CallLeg call, CallEvent event, CallState next) {

        if (event.getState() == SipCallState.ESTABLISHED && next == CallState.CONNECTED
            && call.getState() != CallState.HOLD) {

            call.setResult(CallResult.ANSWERED);

            if (call.getStartedAt() == null) {

                call.setStartedAt(LocalDateTime.now());
            }
        }
    }

    @Override
    protected void scheduleIncomingCallTimeout(CallLeg call, CallEvent event) {

        // Simulate the caller giving up on an unanswered call.
        simulationExecutor.schedule(() -> {

            if (call.getState() == CallState.INCOMING) {

                sipClient.endCall(event.getCallId());
            }
        }, INCOMING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void onCallFinished(CallLeg call) {

        notifyListeners(call);
    }

    /**
     * Stops the simulation executor. Safe to call multiple times.
     * This should be invoked during application shutdown when the
     * mock backend is active.
     */
    public void shutdown() {

        simulationExecutor.shutdownNow();
    }
}
