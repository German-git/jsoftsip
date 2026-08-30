package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.account.SipTransport;
import com.jsoftsip.core.registration.RegistrationListener;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade.ApplyResult;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipSessionRestartTest {

    @TempDir
    Path baresipDir;

    @Test
    void restartRecoversProcessConnectionAndRegistrations() {

        List<String> events = new ArrayList<>();

        FakeRegistrationService registration = new FakeRegistrationService(events);

        registration.registered = List.of(account(1), account(2));

        BaresipSessionRestart restart = new BaresipSessionRestart(new FakeLauncher(events), new FakeSipClient(events),
            registration);

        assertTrue(restart.restart());
        assertEquals(List.of("process-restart", "terminate-calls", "reconnect", "register:1", "register:2"), events,
                     "a restart is a full session restart: process," + " dead-call cleanup, ctrl_tcp reconnect,"
                         + " then re-provisioning");
    }

    @Test
    void constructorWiresTheLauncherWithTheSipClient() {

        FakeLauncher launcher = new FakeLauncher(new ArrayList<>());

        FakeCtrlConnection ctrl = new FakeCtrlConnection();

        BaresipSipClient sipClient = new BaresipSipClient(ctrl);

        sipClient.initialize();

        new BaresipSessionRestart(launcher, sipClient, new FakeRegistrationService(new ArrayList<>()));

        launcher.shutdown();

        assertFalse(ctrl.isConnected(),
                    "the session-restart wiring must let the ordered" + " shutdown disconnect ctrl_tcp");
    }

    @Test
    void processFailureSkipsReconnectButDropsCallState() {

        List<String> events = new ArrayList<>();

        FakeRegistrationService registration = new FakeRegistrationService(events);

        registration.registered = List.of(account(1));

        BaresipSessionRestart restart = new BaresipSessionRestart(new FakeLauncher(events, false),
            new FakeSipClient(events), registration);

        assertFalse(restart.restart());
        assertEquals(List.of("process-restart", "terminate-calls"), events,
                     "the old calls died with the stopped process" + " even though the new one never came up");
    }

    @Test
    void reconnectFailureSkipsReregistrationAndFails() {

        List<String> events = new ArrayList<>();

        FakeRegistrationService registration = new FakeRegistrationService(events);

        registration.registered = List.of(account(1));

        BaresipSessionRestart restart = new BaresipSessionRestart(new FakeLauncher(events),
            new FakeSipClient(events, false), registration);

        assertFalse(restart.restart(),
                    "a dead ctrl_tcp after restart must report" + " failure so the apply flow rolls back");
        assertEquals(List.of("process-restart", "terminate-calls", "reconnect"), events,
                     "accounts must not be provisioned on a dead" + " control socket");
    }

    @Test
    void registrationFailureReportsFailure() {

        List<String> events = new ArrayList<>();

        FakeRegistrationService registration = new FakeRegistrationService(events);

        registration.registered = List.of(account(1));
        registration.failOnRegister = true;

        BaresipSessionRestart restart = new BaresipSessionRestart(new FakeLauncher(events), new FakeSipClient(events),
            registration);

        assertFalse(restart.restart(), "a failed uanew burst must report failure so" + " the apply flow can roll back");
    }

    @Test
    void unregisteredAccountsAreNotResurrected() {

        List<String> events = new ArrayList<>();

        FakeRegistrationService registration = new FakeRegistrationService(events);

        registration.registered = List.of();

        BaresipSessionRestart restart = new BaresipSessionRestart(new FakeLauncher(events), new FakeSipClient(events),
            registration);

        assertTrue(restart.restart());
        assertEquals(List.of("process-restart", "terminate-calls", "reconnect"), events,
                     "accounts the user unregistered must stay" + " offline after the restart");
    }

    @Test
    void orderedRestartDoesNotLogReaderThreadFailure() throws Exception {

        // Real ctrl_tcp peer: the fake process restart closes
        // the accepted socket, exactly like the killed baresip
        // process does under the app reader
        ServerSocket server = new ServerSocket(0);

        BaresipSipClient sipClient = new BaresipSipClient(new BaresipTcpConnection("127.0.0.1", server.getLocalPort()));

        sipClient.initialize();

        Socket accepted = server.accept();

        List<String> events = new ArrayList<>();

        BaresipSessionRestart restart = new BaresipSessionRestart(new KillingLauncher(accepted), sipClient,
            new FakeRegistrationService(events));

        Logger logger = (Logger) LoggerFactory.getLogger("baresip");

        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();

        appender.start();
        logger.addAppender(appender);

        try {

            assertTrue(restart.restart(), "the ordered restart must still succeed");

            // The killed socket is noticed by the stale reader
            // of the old connection, which must have been marked
            // intentional by the pre-restart disconnect
            Thread.sleep(300);

            assertTrue(appender.list.stream().noneMatch(event -> event.getLevel() == Level.ERROR
                && event.getFormattedMessage().contains("Baresip ctrl_tcp" + " reader thread" + " failed")),
                       "the reader death caused by the intentional" + " restart must not log an error");

        } finally {

            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);

            sipClient.shutdown();

            closeQuietly(accepted);
            server.close();
        }
    }

    @Test
    void applyRollbackRerunsTheFullSessionRestart() throws IOException {

        Files.write(baresipDir.resolve("config.lastgood"), List.of("# last known good", "call_hold_other_calls\tno"));

        List<String> events = new ArrayList<>();

        FakeRegistrationService registration = new FakeRegistrationService(events);

        registration.registered = List.of(account(9));

        // The first reconnect fails against the new config,
        // the restore restart must run the whole session
        // recovery again on the restored config
        FakeSipClient sipClient = new FakeSipClient(events, false, true);

        BaresipConfigService service = new BaresipConfigService(baresipDir, new InMemorySettings(),
            new BaresipSessionRestart(new FakeLauncher(events), sipClient, registration), new StubDeviceLister(),
            "127.0.0.1", 4444);

        ApplyResult result = service.apply(Map.of("baresip.call_max_calls", "8"));

        assertEquals(ApplyResult.RESTORED_BACKUP, result,
                     "reconnect failure must trigger the existing" + " rollback path");
        assertEquals(List.of("process-restart", "terminate-calls", "reconnect", "process-restart", "terminate-calls",
                             "reconnect", "register:9"),
                     events, "the restored config gets its own full session" + " restart, ending with re-provisioning");
    }

    private static void closeQuietly(AutoCloseable closeable) {

        if (closeable == null) {
            return;
        }

        try {

            closeable.close();

        } catch (Exception ignored) {
        }
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
     * Launcher fake: scripts process restart outcomes and
     * records every invocation in the shared event log.
     */
    private static final class FakeLauncher extends BaresipLauncher {

        private final Deque<Boolean> outcomes = new ArrayDeque<>();

        private final List<String> events;

        FakeLauncher(List<String> events, boolean... scripted) {

            super("127.0.0.1", 4444, new InMemorySettings(), new StubDeviceLister());

            this.events = events;

            for (boolean outcome : scripted) {
                outcomes.add(outcome);
            }
        }

        @Override
        public boolean restart() {

            events.add("process-restart");

            Boolean outcome = outcomes.poll();

            return outcome == null || outcome;
        }
    }

    /**
     * Launcher fake that kills the ctrl_tcp peer when the
     * process restarts, simulating the real process death
     * under the app reader.
     */
    private static final class KillingLauncher extends BaresipLauncher {

        private final Socket accepted;

        KillingLauncher(Socket accepted) {

            super("127.0.0.1", 4444, new InMemorySettings(), new StubDeviceLister());

            this.accepted = accepted;
        }

        @Override
        public boolean restart() {

            closeQuietly(accepted);

            return true;
        }
    }

    /**
     * SIP client fake: scripts reconnect outcomes and records
     * session cleanup without touching any socket.
     */
    private static final class FakeSipClient extends BaresipSipClient {

        private final Deque<Boolean> reconnectOutcomes = new ArrayDeque<>();

        private final List<String> events;

        FakeSipClient(List<String> events, boolean... reconnectScript) {

            super("127.0.0.1", 1);

            this.events = events;

            for (boolean outcome : reconnectScript) {
                reconnectOutcomes.add(outcome);
            }
        }

        @Override
        public boolean reconnect() {

            events.add("reconnect");

            Boolean outcome = reconnectOutcomes.poll();

            return outcome == null || outcome;
        }

        @Override
        public void terminateSessionCalls() {

            events.add("terminate-calls");
        }
    }

    /**
     * Registration fake: holds the registered-account snapshot
     * and records every re-provisioning call.
     */
    private static final class FakeRegistrationService implements RegistrationService {

        private final List<String> events;

        private List<SipAccount> registered = List.of();

        private boolean failOnRegister;

        FakeRegistrationService(List<String> events) {

            this.events = events;
        }

        @Override
        public void registerAccount(SipAccount account) {

            events.add("register:" + account.getId());

            if (failOnRegister) {
                throw new RuntimeException("uanew failed");
            }
        }

        @Override
        public void unregisterAccount(long accountId) {
        }

        @Override
        public void reprovisionAccount(SipAccount account) {
        }

        @Override
        public List<SipAccount> getRegisteredAccounts() {

            return registered;
        }

        @Override
        public void addRegistrationListener(RegistrationListener listener) {
        }

        @Override
        public void removeRegistrationListener(RegistrationListener listener) {
        }
    }

    /**
     * In-memory SettingsService fake: no database, just a map.
     */
    private static final class InMemorySettings implements SettingsService {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public void saveSetting(String key, String value) {

            values.put(key, value);
        }

        @Override
        public void deleteSetting(String key) {

            values.remove(key);
        }

        @Override
        public Optional<String> getSetting(String key) {

            return Optional.ofNullable(values.get(key));
        }
    }

    /**
     * Device lister stub: no pactl calls in unit tests.
     */
    private static final class StubDeviceLister extends PactlDeviceLister {

        @Override
        public List<String> listSinks() {

            return List.of();
        }

        @Override
        public List<String> listSources() {

            return List.of();
        }
    }
}
