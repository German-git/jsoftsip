package com.jsoftsip.ui.window;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.MockCallService;
import com.jsoftsip.core.call.MockSipClient;
import com.jsoftsip.core.registration.RegistrationListener;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.ui.RecordingCallService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutdownCleanupTest {

    @Test
    void hangsUpEveryActiveCall() {

        MockCallService callService = new MockCallService(new MockSipClient(), new NoOpAccounts(), null);

        SipAccount account = account(1L);

        CallLeg first = callService.startCall(account, "1001@sip.local");
        CallLeg second = callService.startCall(account, "1002@sip.local");

        new ShutdownCleanup(callService, null).hangupActiveCalls();

        assertTrue(callService.getActiveCalls().isEmpty(),
                   "every active call must be terminated via endCall" + " using its backend id");
    }

    @Test
    void hangupWithNoActiveCallsIsANoOp() {

        MockCallService callService = new MockCallService(new MockSipClient(), new NoOpAccounts(), null);

        assertDoesNotThrow(() -> new ShutdownCleanup(callService, null).hangupActiveCalls());
    }

    @Test
    void nullEntryInActiveCallsIsSkipped() {

        CallLeg realCall = new CallLeg();

        realCall.setBackendCallId("call-1");

        RecordingCallService callService = new RecordingCallService();

        callService.seedActiveCalls(Arrays.asList(realCall, null));

        new ShutdownCleanup(callService, null).hangupActiveCalls();

        assertEquals(List.of("call-1"), callService.endedCalls(), "a null entry must not abort the cleanup");
    }

    @Test
    void unregistersEveryRegisteredAccount() {

        FakeRegistrationService registrationService = new FakeRegistrationService(List.of(account(1L), account(2L)));

        new ShutdownCleanup(null, registrationService).unregisterAllAccounts();

        assertEquals(List.of(1L, 2L), registrationService.unregisteredIds());
    }

    @Test
    void runHangsUpCallsAndUnregistersAccounts() {

        MockCallService callService = new MockCallService(new MockSipClient(), new NoOpAccounts(), null);

        SipAccount account = account(1L);

        callService.startCall(account, "1001@sip.local");

        FakeRegistrationService registrationService = new FakeRegistrationService(List.of(account));

        new ShutdownCleanup(callService, registrationService).run();

        assertTrue(callService.getActiveCalls().isEmpty());

        assertEquals(List.of(1L), registrationService.unregisteredIds());
    }

    @Test
    void runToleratesNullServices() {

        assertDoesNotThrow(() -> new ShutdownCleanup(null, null).run());
    }

    @Test
    void runAsyncHangsUpCallsAndUnregistersAccounts() throws ExecutionException, InterruptedException {

        MockCallService callService = new MockCallService(new MockSipClient(), new NoOpAccounts(), null);

        SipAccount account = account(1L);

        callService.startCall(account, "1001@sip.local");

        FakeRegistrationService registrationService = new FakeRegistrationService(List.of(account));

        new ShutdownCleanup(callService, registrationService).runAsync().get();

        assertTrue(callService.getActiveCalls().isEmpty());

        assertEquals(List.of(1L), registrationService.unregisteredIds());
    }

    private static SipAccount account(long id) {

        SipAccount account = new SipAccount();

        account.setId(id);
        account.setUsername("user" + id);
        account.setDomain("example.com");

        return account;
    }

    private static final class FakeRegistrationService implements RegistrationService {

        private final List<SipAccount> registered;

        private final List<Long> unregisteredIds = new ArrayList<>();

        FakeRegistrationService(List<SipAccount> registered) {

            this.registered = new ArrayList<>(registered);
        }

        @Override
        public void registerAccount(SipAccount account) {

            registered.add(account);
        }

        @Override
        public void unregisterAccount(long accountId) {

            unregisteredIds.add(accountId);
        }

        @Override
        public void reprovisionAccount(SipAccount account) {
        }

        @Override
        public List<SipAccount> getRegisteredAccounts() {

            return List.copyOf(registered);
        }

        @Override
        public void addRegistrationListener(RegistrationListener listener) {
        }

        @Override
        public void removeRegistrationListener(RegistrationListener listener) {
        }

        List<Long> unregisteredIds() {

            return List.copyOf(unregisteredIds);
        }
    }

    private static final class NoOpAccounts implements AccountService {

        @Override
        public SipAccount createAccount(SipAccount account) {

            throw new UnsupportedOperationException();
        }

        @Override
        public SipAccount updateAccount(SipAccount account) {

            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteAccount(long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SipAccount> getAccounts() {

            return List.of();
        }

        @Override
        public void updateStatus(long accountId, com.jsoftsip.core.account.AccountStatus status) {

            throw new UnsupportedOperationException();
        }

        @Override
        public void addListener(AccountStatusListener listener) {

            throw new UnsupportedOperationException();
        }

        @Override
        public void rotateMasterKey() {
        }

        @Override
        public void removeListener(AccountStatusListener listener) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SipAccount> findById(long id) {

            return Optional.empty();
        }
    }
}