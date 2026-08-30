package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.sip.SipAccountData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the microphone mute chain: the transport stub
 * remembers the state and both call services forward it.
 */
class MicrophoneMuteTest {

    @Test
    void mockSipClientRemembersMutedState() {

        MockSipClient client = new MockSipClient();

        client.setMicrophoneMuted(true);

        assertTrue(client.isMicrophoneMuted(), "muting must be remembered by the transport stub");

        client.setMicrophoneMuted(false);

        assertFalse(client.isMicrophoneMuted(), "unmuting must be remembered by the transport stub");
    }

    @Test
    void mockCallServiceDelegatesMuteToSipClient() {

        MockSipClient sipClient = new MockSipClient();

        MockCallService service = new MockCallService(sipClient, new InMemoryAccounts(), new NoOpHistoryService());

        service.setMicrophoneMuted(true);

        assertTrue(sipClient.isMicrophoneMuted(), "the mock call service must forward the mute");

        service.setMicrophoneMuted(false);

        assertFalse(sipClient.isMicrophoneMuted(), "the mock call service must forward the unmute");
    }

    @Test
    void defaultCallServiceDelegatesMuteToSipClient() {

        RecordingSipClient sipClient = new RecordingSipClient();

        DefaultCallService service = new DefaultCallService(sipClient, new InMemoryAccounts(),
            new NoOpHistoryService());

        service.setMicrophoneMuted(true);

        assertTrue(sipClient.lastMuted(), "the default call service must forward the mute");

        service.setMicrophoneMuted(false);

        assertFalse(sipClient.lastMuted(), "the default call service must forward the unmute");
    }

    /**
     * Transport fake that only records the last mute value.
     */
    private static final class RecordingSipClient implements SipClient {

        private boolean lastMuted;

        boolean lastMuted() {

            return lastMuted;
        }

        @Override
        public void setMicrophoneMuted(boolean muted) {

            this.lastMuted = muted;
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
        public void addRegistrationListener(SipEventListener listener) {
        }

        @Override
        public void addCallListener(SipCallListener listener) {
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