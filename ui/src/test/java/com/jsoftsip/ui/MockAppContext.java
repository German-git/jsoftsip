package com.jsoftsip.ui;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallListener;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.video.VideoFrameSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Minimal {@link AppContext} test double that wires a
 * {@link SipClient} for controller tests that do not need the
 * full production context. Every service not consumed by the
 * test under exercise throws {@link UnsupportedOperationException}.
 */
public class MockAppContext implements AppContext {

    private final SipClient sipClient;

    private final CallService callService;

    private final AccountService accountService;

    private final SettingsService settingsService;

    private final ExecutorService uiExecutor;

    public MockAppContext(SipClient sipClient) {
        this(sipClient, null, null, null, UiTaskExecutor.global());
    }

    public MockAppContext(SipClient sipClient, ExecutorService uiExecutor) {
        this(sipClient, null, null, null, uiExecutor);
    }

    public MockAppContext(CallService callService, SipClient sipClient) {
        this(sipClient, callService, null, null, UiTaskExecutor.global());
    }

    public MockAppContext(CallService callService, AccountService accountService, SipClient sipClient) {
        this(sipClient, callService, accountService, null, UiTaskExecutor.global());
    }

    public MockAppContext(CallService callService, SettingsService settingsService, SipClient sipClient) {
        this(sipClient, callService, null, settingsService, UiTaskExecutor.global());
    }

    private MockAppContext(SipClient sipClient, CallService callService, AccountService accountService,
                           SettingsService settingsService, ExecutorService uiExecutor) {
        this.sipClient = sipClient;
        this.callService = callService;
        this.accountService = accountService;
        this.settingsService = settingsService;
        this.uiExecutor = uiExecutor;
    }

    @Override
    public ExecutorService getUiExecutor() {
        return uiExecutor;
    }

    @Override
    public Optional<SipClient> getSipClient() {
        return Optional.ofNullable(sipClient);
    }

    @Override
    public AccountService getAccountService() {

        if (accountService != null) {
            return accountService;
        }

        throw new UnsupportedOperationException();
    }

    @Override
    public HistoryService getHistoryService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SettingsService getSettingsService() {

        if (settingsService != null) {
            return settingsService;
        }

        throw new UnsupportedOperationException();
    }

    @Override
    public UiPreferencesService getUiPreferencesService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RegistrationService getRegistrationService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CallService getCallService() {

        if (callService != null) {
            return callService;
        }

        // Minimal video-capable stub for controller tests that
        // exercise the video toggle. The real call operations are
        // out of scope for those tests.
        return new CallService() {

            @Override
            public boolean isVideoSupported() {

                return sipClient != null;
            }

            @Override
            public boolean setVideoTransmissionEnabled(boolean enabled) {

                return sipClient != null && sipClient.setVideoTransmissionEnabled(enabled);
            }

            @Override
            public CallLeg startCall(SipAccount account, String destination) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void endCall(String callId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void holdCall(String callId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void resumeCall(String callId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public List<CallLeg> getActiveCalls() {

                throw new UnsupportedOperationException();
            }

            @Override
            public void addListener(CallListener listener) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void removeListener(CallListener listener) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void answerCall(String callId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void rejectCall(String callId) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void setVolume(int volume) {

                if (sipClient != null) {
                    sipClient.setVolume(volume);
                }
            }

            @Override
            public void setMicrophoneVolume(int volume) {

                if (sipClient != null) {
                    sipClient.setMicrophoneVolume(volume);
                }
            }

            @Override
            public void setMicrophoneMuted(boolean muted) {

                if (sipClient != null) {
                    sipClient.setMicrophoneMuted(muted);
                }
            }
        };
    }

    @Override
    public SelectedAccountContext getSelectedAccountContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<BaresipSettingsFacade> getBaresipSettingsFacade() {
        return Optional.empty();
    }

    @Override
    public Optional<VideoFrameSource> getVideoFrameSource(long accountId) {
        return Optional.empty();
    }
}
