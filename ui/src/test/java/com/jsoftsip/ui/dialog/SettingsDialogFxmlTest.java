package com.jsoftsip.ui.dialog;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallListener;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;
import com.jsoftsip.ui.UiPreferencesService;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that SettingsDialog.fxml can be loaded with the current resource
 * bundle. This catches mismatched %key references at test time instead of
 * runtime.
 */
class SettingsDialogFxmlTest {

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @Test
    void fxmlLoadsWithCurrentResourceBundle() throws IOException {

        FXMLLoader loader = new FXMLLoader(SettingsDialog.class.getResource("/fxml/SettingsDialog.fxml"));
        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new com.jsoftsip.ui.controller.SettingsDialogController(context()));

        Object root = loader.load();

        assertNotNull(root, "SettingsDialog.fxml must load with the current resource bundle");
    }

    private AppContext context() {

        return new MockAppContext(noOpCallService(), inMemorySettingsService(), null) {

            @Override
            public UiPreferencesService getUiPreferencesService() {

                return new UiPreferencesService(inMemorySettingsService());
            }

            @Override
            public com.jsoftsip.core.registration.RegistrationService getRegistrationService() {

                throw new UnsupportedOperationException();
            }
        };
    }

    private SettingsService inMemorySettingsService() {

        return new SettingsService() {

            private final Map<String, String> settings = new HashMap<>();

            @Override
            public void saveSetting(String key, String value) {

                settings.put(key, value);
            }

            @Override
            public void deleteSetting(String key) {

                settings.remove(key);
            }

            @Override
            public Optional<String> getSetting(String key) {

                return Optional.ofNullable(settings.get(key));
            }
        };
    }

    private CallService noOpCallService() {

        return new CallService() {

            @Override
            public CallLeg startCall(com.jsoftsip.core.account.SipAccount account, String destination) {

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

                return List.of();
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

                throw new UnsupportedOperationException();
            }

            @Override
            public void setMicrophoneVolume(int volume) {

                throw new UnsupportedOperationException();
            }

            @Override
            public void setMicrophoneMuted(boolean muted) {

                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isVideoSupported() {

                return false;
            }

            @Override
            public boolean setVideoTransmissionEnabled(boolean enabled) {

                return false;
            }
        };
    }
}
