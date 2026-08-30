package com.jsoftsip.ui.controller;

import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade.ApplyResult;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.DirectExecutorService;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;
import com.jsoftsip.ui.UiPreferencesService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDialogControllerTest {

    private SettingsDialogController controller;

    private TabPane tabPane;

    private ComboBox<?> cmbTheme;

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
        SettingsService settings = new FakeSettingsService();

        AppContext context = new MockAppContext(null, settings, null) {

            private final UiPreferencesService uiPreferencesService = new UiPreferencesService(settings);

            @Override
            public UiPreferencesService getUiPreferencesService() {
                return uiPreferencesService;
            }
        };

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsDialog.fxml"));
        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new SettingsDialogController(context));

        try {
            loader.load();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load SettingsDialog.fxml", exception);
        }

        controller = loader.getController();
        tabPane = (TabPane) loader.getNamespace().get("tabPane");
        cmbTheme = (ComboBox<?>) loader.getNamespace().get("cmbTheme");
    }

    @Test
    void initializeHidesBaresipTabsOnMockBackend() {
        assertNotNull(controller, "controller must be created");
        assertNotNull(tabPane, "tabPane must be injected");

        // MOCK backend: audio/baresip/video/preview tabs are removed,
        // only the General tab remains.
        assertEquals(1, tabPane.getTabs().size());

        // Theme is bound from the UiPreferencesService default.
        assertNotNull(cmbTheme.getValue());
    }

    @Test
    void populatesDeviceCombosAfterFxQueueDrains() throws InterruptedException {

        SettingsService settings = new FakeSettingsService();

        ScriptedBaresipFacade facade = new ScriptedBaresipFacade(List.of("sink_a", "sink_b"), List.of("mic_a"));

        FXMLLoader loader = loadSettingsDialog(settings, facade, new DirectExecutorService());

        ComboBox<?> cmbAudioPlayer = (ComboBox<?>) loader.getNamespace().get("cmbAudioPlayer");
        ComboBox<?> cmbAudioAlert = (ComboBox<?>) loader.getNamespace().get("cmbAudioAlert");
        ComboBox<?> cmbAudioSource = (ComboBox<?>) loader.getNamespace().get("cmbAudioSource");

        // The direct executor ran the fetch inline during load:
        // one sinks round trip feeds both output combos
        assertEquals(1, facade.sinkCalls.get(), "listSinks must be called exactly once for both output combos");
        assertEquals(1, facade.sourceCalls.get(), "listSources must be called exactly once");

        flushFx();

        assertEquals(List.of("sink_a", "sink_b"), cmbAudioPlayer.getItems());
        assertEquals(List.of("sink_a", "sink_b"), cmbAudioAlert.getItems());
        assertEquals(List.of("mic_a"), cmbAudioSource.getItems());

        // The persisted device value seeded by the model must
        // survive the late item population untouched
        assertEquals("alsa,default", cmbAudioPlayer.getEditor().getText(),
                     "late device arrival must not overwrite the editor value");
    }

    @Test
    void showsLoadingPlaceholderUntilFetchCompletes() throws InterruptedException {

        SettingsService settings = new FakeSettingsService();

        ScriptedBaresipFacade facade = new ScriptedBaresipFacade(List.of("sink_a", "sink_b"), List.of("mic_a"));

        // The queued executor parks the fetch, so no runLater is
        // pending and nothing can clear the placeholder before
        // the test releases it deterministically
        QueuedExecutorService uiExecutor = new QueuedExecutorService();

        FXMLLoader loader = loadSettingsDialog(settings, facade, uiExecutor);

        ComboBox<?> cmbAudioPlayer = (ComboBox<?>) loader.getNamespace().get("cmbAudioPlayer");
        ComboBox<?> cmbAudioAlert = (ComboBox<?>) loader.getNamespace().get("cmbAudioAlert");
        ComboBox<?> cmbAudioSource = (ComboBox<?>) loader.getNamespace().get("cmbAudioSource");

        String loadingPrompt = I18n.get("settings.audio.loading.devices");

        assertEquals(loadingPrompt, cmbAudioPlayer.getPromptText(), "player combo must show the loading prompt");

        assertEquals(loadingPrompt, cmbAudioAlert.getPromptText(), "alert combo must show the loading prompt");

        assertEquals(loadingPrompt, cmbAudioSource.getPromptText(), "source combo must show the loading prompt");

        assertTrue(cmbAudioPlayer.getItems().isEmpty(), "devices must not be populated while fetching");

        assertTrue(cmbAudioSource.getItems().isEmpty(), "devices must not be populated while fetching");

        assertEquals(0, facade.sinkCalls.get(), "the fetch must still be parked on the executor");

        uiExecutor.runNext();

        flushFx();

        assertEquals(List.of("sink_a", "sink_b"), cmbAudioPlayer.getItems());

        assertEquals(List.of("sink_a", "sink_b"), cmbAudioAlert.getItems());

        assertEquals(List.of("mic_a"), cmbAudioSource.getItems());

        assertNull(cmbAudioPlayer.getPromptText(), "the placeholder must be cleared once results arrive");
    }

    private FXMLLoader loadSettingsDialog(SettingsService settings, ScriptedBaresipFacade facade,
                                          ExecutorService uiExecutor) {

        AppContext context = new MockAppContext(null, settings, null) {

            private final UiPreferencesService uiPreferencesService = new UiPreferencesService(settings);

            @Override
            public UiPreferencesService getUiPreferencesService() {
                return uiPreferencesService;
            }

            @Override
            public ExecutorService getUiExecutor() {
                return uiExecutor;
            }

            @Override
            public Optional<BaresipSettingsFacade> getBaresipSettingsFacade() {
                return Optional.of(facade);
            }
        };

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SettingsDialog.fxml"));

        loader.setResources(I18n.bundle());
        loader.setControllerFactory(type -> new SettingsDialogController(context));

        try {
            loader.load();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to load SettingsDialog.fxml", exception);
        }

        return loader;
    }

    /**
     * Drains pending Platform.runLater tasks: runLater is FIFO,
     * so once this sentinel executes, every task queued before
     * it has already run.
     */
    private static void flushFx() throws InterruptedException {

        CountDownLatch drained = new CountDownLatch(1);

        Platform.runLater(drained::countDown);

        assertTrue(drained.await(5, TimeUnit.SECONDS), "the FX queue must drain within the timeout");
    }

    /**
     * Test executor that parks every task in a queue until
     * {@link #runNext()} runs it, making the placeholder phase
     * observable without races.
     */
    private static final class QueuedExecutorService extends AbstractExecutorService {

        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable command) {

            tasks.offer(command);
        }

        void runNext() throws InterruptedException {

            tasks.take().run();
        }

        @Override
        public void shutdown() {

            // Nothing to stop: tasks only run when released
        }

        @Override
        public List<Runnable> shutdownNow() {

            return List.of();
        }

        @Override
        public boolean isShutdown() {

            return false;
        }

        @Override
        public boolean isTerminated() {

            return true;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {

            return true;
        }
    }

    /**
     * Fake baresip facade returning scripted device lists and
     * counting how often each list was fetched: the two output
     * combos must share a single listSinks call.
     */
    private static final class ScriptedBaresipFacade implements BaresipSettingsFacade {

        private final List<String> sinks;

        private final List<String> sources;

        private final AtomicInteger sinkCalls = new AtomicInteger();

        private final AtomicInteger sourceCalls = new AtomicInteger();

        ScriptedBaresipFacade(List<String> sinks, List<String> sources) {
            this.sinks = sinks;
            this.sources = sources;
        }

        @Override
        public ApplyResult apply(Map<String, String> values) {

            throw new UnsupportedOperationException();
        }

        @Override
        public String previewPatchedConfig(Map<String, String> pending) {

            return "";
        }

        @Override
        public List<String> readBaseConfigLines() {

            return List.of();
        }

        @Override
        public String previewPatchedConfig(Map<String, String> pending, List<String> baseLines) {

            return "";
        }

        @Override
        public List<String> listSinks() {

            sinkCalls.incrementAndGet();

            return sinks;
        }

        @Override
        public List<String> listSources() {

            sourceCalls.incrementAndGet();

            return sources;
        }
    }

    static class FakeSettingsService implements SettingsService {

        private final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        public void saveSetting(String key, String value) {
            store.put(key, value);
        }

        @Override
        public void deleteSetting(String key) {
            store.remove(key);
        }

        @Override
        public Optional<String> getSetting(String key) {
            return Optional.ofNullable(store.get(key));
        }
    }
}
