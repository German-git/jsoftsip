package com.jsoftsip.launcher;

import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.call.DefaultCallService;
import com.jsoftsip.core.call.MockCallService;
import com.jsoftsip.core.call.MockSipClient;
import com.jsoftsip.core.config.ApplicationBootstrap;
import com.jsoftsip.core.config.ApplicationPaths;
import com.jsoftsip.core.history.DefaultHistoryService;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.infrastructure.RepositoryFactory;
import com.jsoftsip.core.registration.DefaultRegistrationService;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.DefaultAccountService;
import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.core.settings.DefaultSettingsService;
import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.nativebridge.baresip.BaresipConfigService;
import com.jsoftsip.nativebridge.baresip.BaresipLauncher;
import com.jsoftsip.nativebridge.baresip.BaresipSessionRestart;
import com.jsoftsip.nativebridge.baresip.BaresipSipClient;
import com.jsoftsip.nativebridge.baresip.BaresipSupervisor;
import com.jsoftsip.nativebridge.baresip.PactlDeviceLister;
import com.jsoftsip.nativebridge.video.FramePipeAdapter;
import com.jsoftsip.nativebridge.video.NativeFrameTransport;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.video.VideoFrameSource;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.SelectedAccountContext;
import com.jsoftsip.ui.UiPreferencesService;
import com.jsoftsip.ui.UiTaskExecutor;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application composition root: builds and retains every
 * service, starts Baresip and connects before the UI is
 * shown. The UI only sees the {@link AppContext} contract.
 */
public class JSoftSipContext implements AppContext {

    private final AccountService accountService;

    private final HistoryService historyService;

    private final SettingsService settingsService;

    private final UiPreferencesService uiPreferencesService;

    private final RegistrationService registrationService;

    private final CallService callService;

    private final MockCallService mockCallService;

    private final SelectedAccountContext selectedAccountContext = new SelectedAccountContext();

    private final BaresipLauncher baresipLauncher;

    // Crash-recovery owner. Null on the MOCK backend, where no
    // native process exists to supervise.
    private final BaresipSupervisor baresipSupervisor;

    private final BaresipSettingsFacade baresipSettingsFacade;

    // Video frame transport and its routing adapter. Null on the
    // MOCK backend, where no native video process exists.
    private final NativeFrameTransport frameTransport;

    private final FramePipeAdapter framePipeAdapter;

    private final SipClient sipClient;

    private final AtomicBoolean shutdownGuard = new AtomicBoolean(false);

    public JSoftSipContext() {

        ApplicationBootstrap.initialize();

        settingsService = new DefaultSettingsService(RepositoryFactory.settingRepository());

        SipBackend backend = SipBackend.resolve();

        String ctrlTcpHost = settingsService.getSetting(SettingsKeys.BARESIP_CTRL_TCP_HOST)
                                            .orElse(SettingsKeys.BARESIP_CTRL_TCP_HOST_DEFAULT);

        int ctrlTcpPort = resolveCtrlTcpPort();

        // The mock backend needs no native transport: the
        // simulated client lives in the core module and the
        // simulation is owned by MockCallService.

        // Kept beside the generic SipClient reference so the
        // session-restart composite can reach the reconnect
        // and call-cleanup operations once it is wired below.
        BaresipSipClient baresipSipClient = null;

        if (backend == SipBackend.MOCK) {

            baresipLauncher = null;

            frameTransport = null;

            framePipeAdapter = null;

            this.sipClient = new MockSipClient();

        } else {

            baresipLauncher = new BaresipLauncher(ctrlTcpHost, ctrlTcpPort, settingsService, new PactlDeviceLister());

            baresipSipClient = new BaresipSipClient(ctrlTcpHost, ctrlTcpPort, UiTaskExecutor.global());

            this.sipClient = baresipSipClient;

            // The frame adapter routes by AOR and converts to
            // BGRA, the transport listens for the custom jvidisp
            // module. The transport starts before baresip launch
            // below, so the port is bound when jvidisp connects.
            framePipeAdapter = new FramePipeAdapter(baresipSipClient);

            String videoTcpHost = settingsService.getSetting(SettingsKeys.BARESIP_VIDEO_TCP_HOST)
                                                 .orElse(SettingsKeys.BARESIP_VIDEO_TCP_HOST_DEFAULT);

            frameTransport = new NativeFrameTransport(videoTcpHost, resolveVideoTcpPort(), framePipeAdapter);
        }

        accountService = new DefaultAccountService(RepositoryFactory.accountRepository());

        historyService = new DefaultHistoryService(RepositoryFactory.callHistoryRepository());

        // Built before Baresip is launched: launchBaresip() performs
        // the auto-registration from SQLite via this service.
        registrationService = new DefaultRegistrationService(this.sipClient, accountService, settingsService);

        // The call service must be built BEFORE initializeBackend():
        // it registers itself as a listener on the SIP client, so
        // the REGISTERED events of the auto-registration below must
        // not be lost (mock mode schedules incoming calls on them).
        if (backend == SipBackend.MOCK) {

            baresipSettingsFacade = null;

            // No native process exists on the mock backend, so
            // there is nothing to supervise
            baresipSupervisor = null;

            MockSipClient mockSipClient = (MockSipClient) sipClient;

            MockCallService service = new MockCallService(mockSipClient, accountService, historyService);

            mockCallService = service;
            callService = service;

        } else {

            mockCallService = null;

            // Process ownership stays in the launcher: the
            // service only orchestrates config writes. The
            // restart seam is a full SESSION restart (process
            // + ctrl_tcp reconnect + account re-provisioning)
            // so the apply flow never leaves a zombie session.
            // One composite instance is shared with the crash
            // supervisor below, keeping a single recovery
            // primitive in the application.
            BaresipSessionRestart sessionRestart = new BaresipSessionRestart(baresipLauncher, baresipSipClient,
                registrationService);

            baresipSettingsFacade = new BaresipConfigService(ApplicationPaths.getBaresipDirectory(), settingsService,
                sessionRestart, new PactlDeviceLister(), ctrlTcpHost, ctrlTcpPort);

            baresipSupervisor = new BaresipSupervisor(baresipLauncher.getProcessManager(), sessionRestart);

            callService = new DefaultCallService(this.sipClient, accountService, historyService);
        }

        initializeBackend(this.sipClient);

        uiPreferencesService = new UiPreferencesService(settingsService);
    }

    private void initializeBackend(SipClient sipClient) {

        if (baresipLauncher != null) {

            // The video transport must be listening before the
            // custom jvidisp module connects on launch
            if (frameTransport != null) {
                frameTransport.start();
            }

            try {

                baresipLauncher.launch(ApplicationPaths.getBaresipDirectory(), "baresip");

            } catch (IOException | RuntimeException exception) {

                JSoftSipLog.error("Failed to start Baresip", exception);

                // A launch that never answered ctrl_tcp must not
                // leave the video transport listening forever
                if (frameTransport != null) {

                    frameTransport.shutdown();
                }

                throw new RuntimeException("Failed to start Baresip", exception);
            }

            // Supervision arms only after the initial launch
            // succeeded: the launch's own failures must not be
            // treated as crash events to recover from
            if (baresipSupervisor != null) {
                baresipSupervisor.arm();
            }
        }

        sipClient.initialize();

        // Accounts start unregistered on every launch so the
        // user can register each one manually. The persisted
        // status is reset to OFFLINE to avoid showing a stale
        // ONLINE from a previous session.
        accountService.getAccounts()
                      .forEach(account -> accountService.updateStatus(account.getId(), AccountStatus.OFFLINE));
    }

    private int resolveCtrlTcpPort() {

        Optional<String> value = settingsService.getSetting(SettingsKeys.BARESIP_CTRL_TCP_PORT);

        if (value.isPresent()) {

            try {

                return Integer.parseInt(value.get());

            } catch (NumberFormatException exception) {

                // fall back to the default on corrupt values
                JSoftSipLog.warn("Invalid persisted value for " + SettingsKeys.BARESIP_CTRL_TCP_PORT
                    + ", using default", exception);
            }
        }

        return Integer.parseInt(SettingsKeys.BARESIP_CTRL_TCP_PORT_DEFAULT);
    }

    private int resolveVideoTcpPort() {

        Optional<String> value = settingsService.getSetting(SettingsKeys.BARESIP_VIDEO_TCP_PORT);

        if (value.isPresent()) {

            try {

                return Integer.parseInt(value.get());

            } catch (NumberFormatException exception) {

                // fall back to the default on corrupt values
                JSoftSipLog.warn("Invalid persisted value for " + SettingsKeys.BARESIP_VIDEO_TCP_PORT
                    + ", using default", exception);
            }
        }

        return Integer.parseInt(SettingsKeys.BARESIP_VIDEO_TCP_PORT_DEFAULT);
    }

    public void shutdown() {

        // The JavaFX close handler and the application stop()
        // both call shutdown, only the first call may tear down
        if (!shutdownGuard.compareAndSet(false, true)) {

            return;
        }

        // Supervisor first: once the process below is stopped,
        // recovery must already be inert or teardown would be
        // mistaken for a crash and trigger a restart
        if (baresipSupervisor != null) {
            baresipSupervisor.shutdown();
        }

        // Stop registration timers before the rest of the backend
        // so a pending timeout can never fire mid-teardown.
        if (registrationService != null) {
            registrationService.close();
        }

        if (historyService != null) {
            historyService.close();
        }

        // Video first: the reader threads stop polling before
        // the baresip process and its control channel die
        if (frameTransport != null) {
            frameTransport.shutdown();
        }

        if (framePipeAdapter != null) {
            framePipeAdapter.shutdown();
        }

        if (mockCallService != null) {
            mockCallService.shutdown();
        }

        if (baresipLauncher != null) {

            baresipLauncher.shutdown();
        }
    }

    @Override
    public ExecutorService getUiExecutor() {
        return UiTaskExecutor.global();
    }

    @Override
    public AccountService getAccountService() {
        return accountService;
    }

    @Override
    public HistoryService getHistoryService() {
        return historyService;
    }

    @Override
    public SettingsService getSettingsService() {
        return settingsService;
    }

    @Override
    public UiPreferencesService getUiPreferencesService() {
        return uiPreferencesService;
    }

    @Override
    public RegistrationService getRegistrationService() {
        return registrationService;
    }

    @Override
    public CallService getCallService() {
        return callService;
    }

    @Override
    public SelectedAccountContext getSelectedAccountContext() {
        return selectedAccountContext;
    }

    @Override
    public Optional<BaresipSettingsFacade> getBaresipSettingsFacade() {

        return Optional.ofNullable(baresipSettingsFacade);
    }

    @Override
    public Optional<VideoFrameSource> getVideoFrameSource(long accountId) {

        // The adapter routes frames by AOR internally, so the
        // account id only selects the per-account pipe lazily
        return Optional.ofNullable(framePipeAdapter);
    }

    @Override
    public Optional<SipClient> getSipClient() {

        return Optional.ofNullable(sipClient);
    }
}
