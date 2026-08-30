package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.call.CallSession;
import com.jsoftsip.core.call.CallState;
import com.jsoftsip.core.call.DefaultCallService;
import com.jsoftsip.core.call.MockSipClient;
import com.jsoftsip.core.history.CallHistoryEntry;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that DialerDialogController.startCall does not block the
 * JavaFX Application Thread, and that the dial controls are bound to the
 * active session so they lock while the call is active and reset on their
 * own when the session ends (including a remote-end termination).
 */
class DialerDialogControllerTest {

    private static final long CALL_DELAY_MS = 400L;

    private DialerDialogController controller;

    private TextField txtDestination;

    private Button btnCall;

    private Button btnHangup;

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @BeforeEach
    void setUp() {

        SipAccount account = new SipAccount();
        account.setId(1L);
        account.setUsername("1001");
        account.setDomain("demo.org");
        account.setStatus(AccountStatus.ONLINE);

        SlowSipClient sipClient = new SlowSipClient(CALL_DELAY_MS);
        AccountService accountService = new FixedAccountService(account);
        HistoryService historyService = new NoOpHistoryService();
        CallService callService = new DefaultCallService(sipClient, accountService, historyService);

        loadDialer(new MockAppContext(callService, accountService, sipClient), account);
    }

    private void loadDialer(AppContext context, SipAccount account) {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DialerDialog.fxml"));
        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new DialerDialogController(context));

        try {

            loader.load();

        } catch (IOException exception) {

            throw new RuntimeException("Failed to load DialerDialog.fxml", exception);
        }

        controller = loader.getController();
        txtDestination = (TextField) loader.getNamespace().get("txtDestination");
        btnCall = (Button) loader.getNamespace().get("btnCall");
        btnHangup = (Button) loader.getNamespace().get("btnHangup");

        controller.setAccount(account);
    }

    @Test
    void startCallBindsControlsToSessionAndResetsOnEnd() throws Exception {

        AtomicLong elapsed = new AtomicLong();
        CountDownLatch fired = new CountDownLatch(1);

        Platform.runLater(() -> {

            txtDestination.setText("1234");
            long start = System.currentTimeMillis();
            btnCall.fire();
            elapsed.set(System.currentTimeMillis() - start);
            fired.countDown();
        });

        assertTrue(fired.await(5, TimeUnit.SECONDS), "btnCall.fire() must complete on the FX thread");

        assertTrue(elapsed.get() < 200, "startCall must return within 200 ms (was " + elapsed.get() + " ms)");

        // While the session is active the controls must be driven by the binding:
        // the call button and destination are locked, the hangup button is live.
        await(() -> btnCall.isDisabled(), 5, "call button should be disabled once the session is active");
        assertFalse(btnHangup.isDisabled(), "hangup must be enabled while the call is active");
        assertTrue(txtDestination.isDisabled(), "destination must be disabled while the call is active");

        // Ending the call (remote or local) makes the session inactive and the
        // binding must reset the controls automatically, with no imperative
        // reset and no reliance on a per-call listener.
        Platform.runLater(() -> btnHangup.fire());

        await(() -> !btnCall.isDisabled(), 5, "call button must reset when the session ends");
        assertTrue(btnHangup.isDisabled(), "hangup must reset when the session ends");
        assertFalse(txtDestination.isDisabled(), "destination must reset when the session ends");
    }

    @Test
    void hangupEndsFirstLegOfSessionWhenNoLegMatchesTheAccount() throws Exception {

        SipAccount account = new SipAccount();
        account.setId(1L);
        account.setUsername("1001");
        account.setDomain("demo.org");
        account.setStatus(AccountStatus.ONLINE);

        // The session leg carries a different account id than the dialer
        // account, so the by-account match finds nothing and the fallback
        // must end the first leg of the session.
        SipAccount legAccount = new SipAccount();
        legAccount.setId(2L);
        legAccount.setUsername("1002");
        legAccount.setDomain("demo.org");

        RecordingSipClient sipClient = new RecordingSipClient(0);
        AccountService accountService = new FixedAccountService(account);
        HistoryService historyService = new NoOpHistoryService();
        CallService callService = new SwappedLegAccountCallService(sipClient, accountService, historyService,
            legAccount);

        loadDialer(new MockAppContext(callService, accountService, sipClient), account);

        Platform.runLater(() -> {

            txtDestination.setText("1234");
            btnCall.fire();
        });

        await(() -> !btnHangup.isDisabled(), 5, "hangup must be enabled once the session is active");

        String legBackendId = callService.getActiveCalls().get(0).getBackendCallId();

        Platform.runLater(() -> btnHangup.fire());

        await(() -> !sipClient.endedCallIds.isEmpty(), 5, "hangup must end a leg even when none matches the account");

        assertEquals(legBackendId, sipClient.endedCallIds.get(0),
                     "without a by-account match the fallback must end the first leg of the session");

        await(() -> !btnCall.isDisabled(), 5, "call button must reset when the session ends");
    }

    @Test
    void hangupMatchesLegByAccountIdValueNotReference() throws Exception {

        // The account id is a boxed Long and the session leg may carry a
        // different instance than the one the dialer holds (the backend
        // re-resolves accounts). Outside the Long cache range (-128..127)
        // a == comparison silently fails, so matching must use equals.
        SipAccount account = new SipAccount();
        account.setId(1000L);
        account.setUsername("1001");
        account.setDomain("demo.org");
        account.setStatus(AccountStatus.ONLINE);

        SipAccount legAccount = new SipAccount();
        legAccount.setId(1000L);
        legAccount.setUsername("1001");
        legAccount.setDomain("demo.org");

        SipAccount peerAccount = new SipAccount();
        peerAccount.setId(2L);
        peerAccount.setUsername("1002");
        peerAccount.setDomain("demo.org");
        peerAccount.setStatus(AccountStatus.ONLINE);

        RecordingSipClient sipClient = new RecordingSipClient(0);
        AccountService accountService = new FixedAccountService(peerAccount);
        HistoryService historyService = new NoOpHistoryService();
        CallService callService = new SwappedLegAccountCallService(sipClient, accountService, historyService,
            legAccount);

        loadDialer(new MockAppContext(callService, accountService, sipClient), account);

        // A ringing incoming leg from the peer lands first in the session
        // that the dialer's outgoing leg joins right after, so a fallback
        // to the first leg would end the WRONG leg.
        sipClient.simulateIncomingCall(2L, "sip:1001@demo.org");

        Platform.runLater(() -> {

            txtDestination.setText("1002");
            btnCall.fire();
        });

        await(() -> !btnHangup.isDisabled(), 5, "hangup must be enabled once the session is active");

        String outgoingBackendId = callService.getActiveCalls().stream()
                                              .filter(leg -> leg.getDirection() == CallDirection.OUTGOING).findFirst()
                                              .orElseThrow().getBackendCallId();

        Platform.runLater(() -> btnHangup.fire());

        await(() -> !sipClient.endedCallIds.isEmpty(), 5, "hangup must end a leg");

        assertEquals(outgoingBackendId, sipClient.endedCallIds.get(0),
                     "the leg matched by account id must end first, not the fallback first leg");
    }

    @Test
    void hangupPrefersTheLiveLegOverAnEndedLegOfTheSameAccount() throws Exception {

        // Regression harness for the redial hangup no-op: the session holds
        // a stale ENDED leg for this account ahead of the live leg, which is
        // exactly what a redial produced before finished legs were evicted
        // from their session.
        SipAccount account = new SipAccount();
        account.setId(1L);
        account.setUsername("1001");
        account.setDomain("demo.org");
        account.setStatus(AccountStatus.ONLINE);

        RecordingSipClient sipClient = new RecordingSipClient(0);
        AccountService accountService = new FixedAccountService(account);
        HistoryService historyService = new NoOpHistoryService();
        EndedThenLiveLegCallService callService = new EndedThenLiveLegCallService(sipClient, accountService,
            historyService);

        loadDialer(new MockAppContext(callService, accountService, sipClient), account);

        Platform.runLater(() -> {

            txtDestination.setText("1002");
            btnCall.fire();
        });

        await(() -> !btnHangup.isDisabled(), 5, "hangup must be enabled once the session is active");

        String liveBackendId = callService.getActiveCalls().get(0).getBackendCallId();

        Platform.runLater(() -> btnHangup.fire());

        await(() -> !callService.requestedEndIds.isEmpty(), 5, "hangup must request ending a leg");

        assertEquals(liveBackendId, callService.requestedEndIds.get(0),
                     "hangup must end the live leg of the session, not a stale ended leg of the same account");
    }

    private void await(BooleanSupplier condition, int timeoutSeconds, String message) throws InterruptedException {

        for (int i = 0; i < timeoutSeconds * 10; i++) {

            if (condition.getAsBoolean()) {
                return;
            }

            Thread.sleep(100);
        }

        fail(message);
    }

    private static class SlowSipClient extends MockSipClient {

        private final long delayMillis;

        SlowSipClient(long delayMillis) {

            this.delayMillis = delayMillis;
        }

        @Override
        public String startCall(long accountId, String destination) {

            try {

                Thread.sleep(delayMillis);

            } catch (InterruptedException exception) {

                Thread.currentThread().interrupt();
            }

            return super.startCall(accountId, destination);
        }
    }

    /**
     * Sip client double that records the backend call ids passed to
     * endCall, so tests can assert which leg the dialer chose to end.
     */
    private static class RecordingSipClient extends SlowSipClient {

        private final List<String> endedCallIds = new CopyOnWriteArrayList<>();

        RecordingSipClient(long delayMillis) {

            super(delayMillis);
        }

        @Override
        public void endCall(String callId) {

            endedCallIds.add(callId);

            super.endCall(callId);
        }
    }

    /**
     * Call service double whose outgoing legs carry a different account
     * instance than the one the dialer passed in, reproducing the real
     * backend behavior where the leg account is re-resolved and is not
     * the same object the controller holds.
     */
    private static class SwappedLegAccountCallService extends DefaultCallService {

        private final SipAccount legAccount;

        SwappedLegAccountCallService(SipClient sipClient, AccountService accountService, HistoryService historyService,
                                     SipAccount legAccount) {

            super(sipClient, accountService, historyService);

            this.legAccount = legAccount;
        }

        @Override
        public CallLeg startCall(SipAccount account, String destination) {

            CallLeg leg = super.startCall(account, destination);

            leg.setAccount(legAccount);

            return leg;
        }
    }

    /**
     * Call service double whose startCall returns the live leg inside a
     * session that already carries an ENDED leg for the same account,
     * reproducing the stale session a redial used to hit before finished
     * legs were evicted from their session. Records every endCall request
     * so the test can assert which leg the dialer chose to end.
     */
    private static class EndedThenLiveLegCallService extends DefaultCallService {

        private final List<String> requestedEndIds = new CopyOnWriteArrayList<>();

        EndedThenLiveLegCallService(SipClient sipClient, AccountService accountService, HistoryService historyService) {

            super(sipClient, accountService, historyService);
        }

        @Override
        public CallLeg startCall(SipAccount account, String destination) {

            CallLeg liveLeg = super.startCall(account, destination);

            CallLeg endedLeg = new CallLeg();
            endedLeg.setAccount(account);
            endedLeg.setDirection(CallDirection.OUTGOING);
            endedLeg.setBackendCallId("ended-" + liveLeg.getBackendCallId());
            endedLeg.setState(CallState.ENDED);

            CallSession session = new CallSession("stale-redial");
            session.addLeg(endedLeg);
            session.addLeg(liveLeg);

            return liveLeg;
        }

        @Override
        public void endCall(String callId) {

            requestedEndIds.add(callId);

            super.endCall(callId);
        }
    }

    private static class FixedAccountService implements AccountService {

        private final SipAccount account;

        FixedAccountService(SipAccount account) {

            this.account = account;
        }

        @Override
        public Optional<SipAccount> findById(long id) {

            return Optional.of(account);
        }

        @Override
        public List<SipAccount> getAccounts() {

            return List.of(account);
        }

        @Override
        public void updateStatus(long accountId, AccountStatus status) {
        }

        @Override
        public void addListener(AccountStatusListener listener) {
        }

        @Override
        public void rotateMasterKey() {
        }

        @Override
        public void removeListener(AccountStatusListener listener) {
        }

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
    }

    private static class NoOpHistoryService implements HistoryService {

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
