package com.jsoftsip.core.registration;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.sip.SipCallListener;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.sip.SipEventListener;
import com.jsoftsip.core.sip.SipRegistrationEvent;
import com.jsoftsip.core.sip.SipAccountData;
import com.jsoftsip.core.sip.SipRegistrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRegistrationServiceTest {

    private DefaultRegistrationService service;

    private FakeSipClient client;

    private InMemoryAccounts accounts;

    @BeforeEach
    void setUp() {

        accounts = new InMemoryAccounts();

        client = new FakeSipClient();

        service = new DefaultRegistrationService(client, accounts, new FakeSettingsService());
    }

    @Test
    void registeredAccountIsAvailableForSessionRecovery() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERED));

        assertEquals(List.of(1L), registeredIds(),
                     "an account with a live registration must be" + " re-provisioned after a restart");
    }

    @Test
    void registeringAccountIsKeptForSessionRecovery() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERING));

        assertEquals(List.of(1L), registeredIds(), "uanew was already sent, so a restart landing"
            + " mid-registration must not lose the" + " account");
    }

    @Test
    void unregisteredAccountIsNotResurrected() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERED));

        service.onRegistrationEvent(event(1, SipRegistrationState.UNREGISTERED));

        assertTrue(service.getRegisteredAccounts().isEmpty(),
                   "an account the user unregistered must stay" + " offline across a restart");
    }

    @Test
    void failedRegistrationIsNotResurrected() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.FAILED));

        assertTrue(service.getRegisteredAccounts().isEmpty(),
                   "a failed registration maps to OFFLINE and must" + " not come back after a restart");
    }

    @Test
    void failedRegistrationDropsTheBaresipUa() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.FAILED));

        service.awaitStatusFlushed();

        assertEquals(List.of(1L), client.unregisteredIds(), "a failed UA must be dropped from baresip so it"
            + " cannot re-register with stale" + " credentials");

        assertEquals(AccountStatus.OFFLINE, accounts.statusOf(1L), "a failed registration must map to OFFLINE");
    }

    @Test
    void onlyFailedEventsDropTheBaresipUa() {

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERING));

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERED));

        service.onRegistrationEvent(event(1, SipRegistrationState.UNREGISTERED));

        assertTrue(client.unregisteredIds().isEmpty(), "only a FAILED event must drop the UA from" + " baresip");
    }

    @Test
    void reprovisioningARegisteredAccountReRegistersOnTeardown() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERED));

        SipAccount updated = account(1);

        updated.setUsername("newuser");

        updated.setPassword("newpass");

        updated.setDomain("new.example.com");

        updated.setTransport(SipTransport.TLS);

        service.reprovisionAccount(updated);

        assertEquals(List.of(1L), client.unregisteredIds(), "reprovisioning must drop the old UA first");

        assertEquals(1, client.registeredData().size(),
                     "the new UA must not be created before the" + " teardown event lands");

        service.onRegistrationEvent(event(1, SipRegistrationState.UNREGISTERED));

        assertEquals(2, client.registeredData().size(), "the pending account must be re-registered once"
            + " the teardown event confirms the old UA" + " is gone");

        SipAccountData data = client.registeredData().get(1);

        assertEquals("newuser", data.getUsername());

        assertEquals("newpass", data.getPassword());

        assertEquals("new.example.com", data.getDomain());

        assertEquals("TLS", data.getTransport());
    }

    @Test
    void reprovisioningAnUnregisteredAccountIsANoOp() {

        accounts.createAccount(account(1));

        service.reprovisionAccount(account(1));

        assertTrue(client.unregisteredIds().isEmpty(), "an account without a live registration has no" + " UA to drop");

        assertTrue(client.registeredData().isEmpty(),
                   "an account without a live registration must not" + " be re-registered");
    }

    @Test
    void reprovisioningAFailedAccountIsANoOp() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.FAILED));

        service.reprovisionAccount(account(1));

        assertEquals(List.of(1L), client.unregisteredIds(), "only the FAILED cleanup must drop the UA, a"
            + " reprovision of a failed account is a" + " no-op");
    }

    @Test
    void unregisteredEventWithoutPendingBehavesNormally() {

        accounts.createAccount(account(1));

        service.registerAccount(account(1));

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERED));

        service.onRegistrationEvent(event(1, SipRegistrationState.UNREGISTERED));

        assertTrue(service.getRegisteredAccounts().isEmpty(),
                   "an UNREGISTERED event without a pending" + " reprovision must drop the account");

        assertEquals(1, client.registeredData().size(),
                     "an UNREGISTERED event without a pending" + " reprovision must not re-register");
    }

    @Test
    void neverRegisteredAccountIsNotResurrected() {

        assertTrue(service.getRegisteredAccounts().isEmpty());
    }

    @Test
    void failedRegistrationIsReDispatchedToOwnListeners() {

        List<SipRegistrationEvent> received = new ArrayList<>();

        RegistrationListener listener = received::add;

        service.addRegistrationListener(listener);

        SipRegistrationEvent event = event(1, SipRegistrationState.FAILED);

        service.onRegistrationEvent(event);

        assertEquals(List.of(event), received, "a FAILED registration event must reach the" + " service listeners");
    }

    @Test
    void registeredRegistrationIsReDispatchedToOwnListeners() {

        List<SipRegistrationEvent> received = new ArrayList<>();

        service.addRegistrationListener(received::add);

        service.onRegistrationEvent(event(1, SipRegistrationState.REGISTERED));

        assertEquals(1, received.size(), "a REGISTERED registration event must reach the" + " service listeners");
    }

    @Test
    void removedListenerStopsReceivingRegistrationEvents() {

        List<SipRegistrationEvent> received = new ArrayList<>();

        RegistrationListener listener = received::add;

        service.addRegistrationListener(listener);

        service.removeRegistrationListener(listener);

        service.onRegistrationEvent(event(1, SipRegistrationState.FAILED));

        assertTrue(received.isEmpty(), "a removed listener must not receive" + " registration events");
    }

    private List<Long> registeredIds() {

        return service.getRegisteredAccounts().stream().map(SipAccount::getId).toList();
    }

    private static SipRegistrationEvent event(long accountId, SipRegistrationState state) {

        return new SipRegistrationEvent(accountId, state, "sip:user" + accountId + "@example.com");
    }

    private static SipAccount account(long id) {

        SipAccount account = new SipAccount();

        account.setId(id);
        account.setUsername("user" + id);
        account.setPassword("secret");
        account.setDomain("example.com");
        account.setTransport(SipTransport.UDP);

        return account;
    }

    /**
     * SIP client fake: no transport, just enough surface for
     * the registration service to subscribe and send.
     */
    private static final class FakeSipClient implements SipClient {

        private final List<SipAccountData> registered = new ArrayList<>();

        private final List<Long> unregistered = new ArrayList<>();

        @Override
        public void initialize() {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void registerAccount(SipAccountData account) {

            registered.add(account);
        }

        @Override
        public void unregisterAccount(long accountId) {

            unregistered.add(accountId);
        }

        List<SipAccountData> registeredData() {

            return List.copyOf(registered);
        }

        List<Long> unregisteredIds() {

            return List.copyOf(unregistered);
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
    }

    /**
     * Account service fake: accounts live in a plain map.
     */
    private static final class InMemoryAccounts implements AccountService {

        private final Map<Long, SipAccount> accounts = new HashMap<>();

        private final Map<Long, AccountStatus> statuses = new HashMap<>();

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

            statuses.put(accountId, status);
        }

        AccountStatus statusOf(long accountId) {

            return statuses.get(accountId);
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
     * Settings service fake: returns defaults for all keys.
     */
    private static final class FakeSettingsService implements SettingsService {

        @Override
        public void saveSetting(String key, String value) {
        }

        @Override
        public void deleteSetting(String key) {
        }

        @Override
        public Optional<String> getSetting(String key) {

            return Optional.empty();
        }
    }
}
