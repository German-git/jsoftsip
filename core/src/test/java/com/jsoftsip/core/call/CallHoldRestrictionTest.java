package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.core.sip.CallEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipCallState;
import com.jsoftsip.core.sip.SipEventListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the hold/resume passthrough contract: the service
 * forwards the command to
 * the backend under the session monitor and never performs an
 * optimistic local transition nor notifies listeners from the
 * command itself. State changes arrive only through backend
 * events, so a dropped baresip action cannot strand a leg in
 * eternal HOLD and remote hold keeps working.
 */
class CallHoldRestrictionTest {

    private static final String CALL_ID = "call-1";

    private static SipAccount account() {

        SipAccount account = new SipAccount();

        account.setId(1L);
        account.setUsername("user1");
        account.setPassword("secret");
        account.setDomain("example.com");

        return account;
    }

    @Test
    void defaultServiceHoldSendsCommandWithoutMutatingState() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        CallLeg call = service.startCall(account(), "1001");

        listener.notifications.clear();

        service.holdCall(call.getBackendCallId());

        assertEquals(List.of(CALL_ID), sipClient.holdCalls, "the hold command must reach the backend");

        assertEquals(CallState.DIALING, call.getState(), "holdCall must not mutate the local state");

        assertTrue(listener.notifications.isEmpty(), "holdCall must not notify listeners");
    }

    @Test
    void defaultServiceHoldOnConnectedCallIsPurePassthrough() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        CallLeg call = service.startCall(account(), "1001");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1001", SipCallState.ESTABLISHED));

        assertEquals(CallState.CONNECTED, call.getState(), "the established event must connect the call");

        listener.notifications.clear();

        service.holdCall(call.getBackendCallId());

        assertEquals(List.of(CALL_ID), sipClient.holdCalls, "the hold command must reach the backend");

        assertEquals(CallState.CONNECTED, call.getState(), "holdCall must not move the leg to HOLD locally");

        assertTrue(listener.notifications.isEmpty(), "holdCall must not notify listeners");
    }

    @Test
    void defaultServiceResumeSendsCommandWithoutMutatingState() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        RecordingCallListener listener = new RecordingCallListener();

        service.addListener(listener);

        CallLeg call = service.startCall(account(), "1001");

        sipClient.injectEvent(new CallEvent(call.getBackendCallId(), 1L, "1001", SipCallState.ESTABLISHED));

        assertEquals(CallState.CONNECTED, call.getState(), "the established event must connect the call");

        listener.notifications.clear();

        service.resumeCall(call.getBackendCallId());

        assertEquals(List.of(CALL_ID), sipClient.resumeCalls, "the resume command must reach the backend");

        assertEquals(CallState.CONNECTED, call.getState(), "resumeCall must not mutate the local state");

        assertTrue(listener.notifications.isEmpty(), "resumeCall must not notify listeners");
    }

    @Test
    void mockServiceHoldOnPreConnectCallSendsCommandWithoutMutatingState() {

        MockSipClient sipClient = new MockSipClient();

        RecordingSipCallListener transportListener = new RecordingSipCallListener();

        sipClient.addCallListener(transportListener);

        MockCallService service = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        CallLeg call = service.startCall(account(), "1001");

        transportListener.states.clear();

        service.holdCall(call.getBackendCallId());

        assertEquals(List.of(SipCallState.HOLD), transportListener.states,
                     "the hold command must reach the backend without transition validation");

        assertEquals(CallState.DIALING, call.getState(), "holding a pre-connect call must not change its state");
    }

    @Test
    void mockServiceHoldOnConnectedCallEmitsHoldEvent() {

        MockSipClient sipClient = new MockSipClient();

        RecordingSipCallListener transportListener = new RecordingSipCallListener();

        sipClient.addCallListener(transportListener);

        MockCallService service = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        CallLeg call = service.startCall(account(), "1001");

        sipClient.simulateEstablished(call.getBackendCallId(), 1L, "1001");

        assertEquals(CallState.CONNECTED, call.getState(), "the established event must connect the call");

        transportListener.states.clear();

        service.holdCall(call.getBackendCallId());

        assertEquals(List.of(SipCallState.HOLD), transportListener.states, "the hold command must reach the backend");

        assertEquals(CallState.HOLD, call.getState(), "the HOLD backend event must move the call to HOLD");
    }

    @Test
    void mockServiceResumeOnConnectedCallIsPassthrough() {

        MockSipClient sipClient = new MockSipClient();

        RecordingSipCallListener transportListener = new RecordingSipCallListener();

        sipClient.addCallListener(transportListener);

        MockCallService service = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        CallLeg call = service.startCall(account(), "1001");

        sipClient.simulateEstablished(call.getBackendCallId(), 1L, "1001");

        assertEquals(CallState.CONNECTED, call.getState(), "the established event must connect the call");

        transportListener.states.clear();

        service.resumeCall(call.getBackendCallId());

        assertEquals(List.of(SipCallState.ESTABLISHED), transportListener.states,
                     "the resume command must reach the backend without transition validation");

        assertEquals(CallState.CONNECTED, call.getState(),
                     "resumeCall must not mutate the local state beyond the event echo");
    }

    /**
     * Transport fake that records hold and resume commands and
     * lets the test inject backend events into the service.
     */
    private static final class RecordingSipClient implements SipClient {

        private final List<String> holdCalls = new ArrayList<>();

        private final List<String> resumeCalls = new ArrayList<>();

        private SipCallListener callListener;

        @Override
        public void holdCall(String callId) {

            holdCalls.add(callId);
        }

        @Override
        public void resumeCall(String callId) {

            resumeCalls.add(callId);
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

            return CALL_ID;
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
    }

    /**
     * Service listener fake: records every notification.
     */
    private static final class RecordingCallListener implements CallListener {

        private final List<CallLeg> notifications = new ArrayList<>();

        @Override
        public void onCallChanged(CallLeg call) {

            notifications.add(call);
        }
    }

    /**
     * Transport listener fake: records every call event state.
     */
    private static final class RecordingSipCallListener implements SipCallListener {

        private final List<SipCallState> states = new ArrayList<>();

        @Override
        public void onCallEvent(CallEvent event) {

            states.add(event.getState());
        }
    }

    /**
     * Account service fake: accounts live in a plain map.
     */
    private static final class InMemoryAccounts implements AccountService {

        private final Map<Long, SipAccount> accounts = new HashMap<>();

        @Override
        public SipAccount createAccount(SipAccount account) {

            accounts.put(account.getId(), account);

            return account;
        }

        @Override
        public SipAccount updateAccount(SipAccount account) {

            accounts.put(account.getId(), account);

            return account;
        }

        @Override
        public void deleteAccount(long id) {

            accounts.remove(id);
        }

        @Override
        public List<SipAccount> getAccounts() {

            return List.copyOf(accounts.values());
        }

        @Override
        public void updateStatus(long accountId, com.jsoftsip.core.account.AccountStatus status) {
        }

        @Override
        public void addListener(AccountStatusListener listener) {
        }

        @Override
        public void rotateMasterKey() {
        }

        public void removeListener(AccountStatusListener listener) {
        }

        @Override
        public Optional<SipAccount> findById(long id) {

            return Optional.ofNullable(accounts.get(id));
        }
    }

    /**
     * History service fake: nothing to record.
     */
    private static final class NoOpHistoryService implements HistoryService {

        @Override
        public void registerFinishedCall(CallLeg call) {
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
}