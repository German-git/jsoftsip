package com.jsoftsip.ui;

import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.history.HistoryService;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.core.sip.SipClient;
import com.jsoftsip.core.video.VideoFrameSource;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Composition root contract consumed by the UI layer.
 * Implemented by the launcher module and injected into
 * controllers, the UI never resolves services statically.
 */
public interface AppContext {

    /**
     * Executor for short, IO-bound background tasks that must not
     * block the JavaFX Application Thread (pactl, ctrl_tcp dial).
     */
    ExecutorService getUiExecutor();

    AccountService getAccountService();

    HistoryService getHistoryService();

    SettingsService getSettingsService();

    UiPreferencesService getUiPreferencesService();

    RegistrationService getRegistrationService();

    CallService getCallService();

    SelectedAccountContext getSelectedAccountContext();

    /**
     * Baresip settings apply/preview port. Empty on the MOCK
     * backend, where no native process exists to reconfigure.
     */
    Optional<BaresipSettingsFacade> getBaresipSettingsFacade();

    /**
     * Video frame source for the given account. Empty on the
     * MOCK backend, where no frame transport exists: the UI
     * renders the placeholder.
     */
    Optional<VideoFrameSource> getVideoFrameSource(long accountId);

    /**
     * SIP client transport (baresip ctrl_tcp or the in-memory
     * mock). Empty when no SIP backend is configured.
     */
    Optional<SipClient> getSipClient();
}
