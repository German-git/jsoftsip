package com.jsoftsip.ui.controller;

import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;
import com.jsoftsip.ui.RecordingCallService;
import com.jsoftsip.ui.SelectedAccountContext;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Slider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that rapid volume slider movements are debounced and do not
 * synchronously flood the CallService with setVolume calls.
 */
class TopToolbarControllerTest {

    private static final int INITIAL_VOLUME = 50;

    private RecordingCallService recordingCallService;

    private Slider volumeSlider;

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @BeforeEach
    void setUp() throws Exception {

        Map<String, String> settings = new HashMap<>();
        settings.put(SettingsKeys.VOLUME_OUTPUT, String.valueOf(INITIAL_VOLUME));
        settings.put(SettingsKeys.VOLUME_MICROPHONE, String.valueOf(INITIAL_VOLUME));

        SettingsService settingsService = new InMemorySettingsService(settings);
        recordingCallService = new RecordingCallService();

        AppContext context = new MockAppContext(recordingCallService, settingsService, null) {

            @Override
            public SelectedAccountContext getSelectedAccountContext() {

                return new SelectedAccountContext();
            }
        };

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TopToolbar.fxml"));
        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new TopToolbarController(context));

        try {

            loader.load();

        } catch (IOException exception) {

            throw new RuntimeException("Failed to load TopToolbar.fxml", exception);
        }

        volumeSlider = (Slider) loader.getNamespace().get("volumeSlider");

        // Wait for the initial startup volume call to complete before exercising the slider.
        assertTrue(recordingCallService.awaitOutputVolumeCount(1, 2000), "initial volume call must complete");
    }

    @Test
    void rapidSliderChangesAreDebouncedToASingleSetVolume() throws Exception {

        int initialCount = recordingCallService.outputVolumes().size();

        CountDownLatch changesDone = new CountDownLatch(1);

        Platform.runLater(() -> {

            for (int i = 0; i < 10; i++) {
                volumeSlider.setValue(i * 10);
            }

            changesDone.countDown();
        });

        assertTrue(changesDone.await(5, TimeUnit.SECONDS), "slider changes must be scheduled on the FX thread");

        // Wait for the 100 ms debounce window plus task scheduling overhead.
        Thread.sleep(400);

        int finalCount = recordingCallService.outputVolumes().size();

        assertEquals(initialCount + 1, finalCount,
                     "rapid slider movements must be debounced to a single setVolume call");

        assertEquals(90, recordingCallService.outputVolumes().get(recordingCallService.outputVolumes().size() - 1),
                     "the last value after debounce must be the most recent slider value");
    }

    private static class InMemorySettingsService implements SettingsService {

        private final Map<String, String> settings;

        InMemorySettingsService(Map<String, String> settings) {

            this.settings = new HashMap<>(settings);
        }

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
    }
}
