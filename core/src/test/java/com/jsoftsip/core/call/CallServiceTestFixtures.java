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
import com.jsoftsip.core.sip.SipEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared fakes for the call-service tests: a SIP client that can
 * inject backend events, an in-memory account service and a
 * no-op history service.
 */
final class CallServiceTestFixtures {

    private CallServiceTestFixtures() {
    }

    static SipAccount account(long id, String username) {

        SipAccount account = new SipAccount();

        account.setId(id);

        account.setUsername(username);

        account.setPassword("secret");

        account.setDomain("example.com");

        return account;
    }

    /**
     * Transport fake that lets the test inject backend events
     * into the service.
     */
    static final class RecordingSipClient implements SipClient {

        private SipCallListener callListener;

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

            return "call-1";
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

            this.callListener = listener;
        }
    }

    /**
     * Account service fake: accounts live in a plain map.
     */
    static final class InMemoryAccounts implements AccountService {

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
    static final class NoOpHistoryService implements HistoryService {

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