package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.config.PrivateFiles;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade.ApplyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipConfigServiceTest {

    private static final String CTRL_HOST = "127.0.0.1";

    private static final int CTRL_PORT = 4444;

    @TempDir
    Path baresipDir;

    private InMemorySettings settings;

    private StubDeviceLister lister;

    @BeforeEach
    void setUp() {

        settings = new InMemorySettings();

        lister = new StubDeviceLister();
    }

    private BaresipSettingsFacade newService(FakeRestart restart) {

        return new BaresipConfigService(baresipDir, settings, restart, lister, CTRL_HOST, CTRL_PORT);
    }

    @Test
    void successfulApplyPatchesConfigAndReturnsApplied() throws IOException {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        ApplyResult result = facade.apply(Map.of("baresip.call_max_calls", "8"));

        assertEquals(ApplyResult.APPLIED, result);

        List<String> config = Files.readAllLines(baresipDir.resolve("config"));

        assertTrue(config.contains("call_max_calls\t8"), "DB value must be patched into the config");
        assertEquals("call_hold_other_calls\tno", config.get(config.size() - 1),
                     "forced hold=no must be the last config line");
        assertTrue(config.contains("module_app\t\tctrl_tcp.so"), "no base config means minimal fallback is the base");
        assertTrue(PrivateFiles.isOwnerOnly(baresipDir.resolve("config")),
                   "the patched config must be owner-only (0600)");
    }

    @Test
    void successfulApplyLeavesNoTemporaryConfigFile() {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        facade.apply(Map.of("baresip.call_max_calls", "8"));

        assertFalse(Files.exists(baresipDir.resolve("config.tmp")),
                    "the atomic write must not leave a temporary config file");
    }

    @Test
    void successfulApplyPersistsValuesViaSettingsService() {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        facade.apply(Map.of("baresip.call_max_calls", "8"));

        assertEquals(Optional.of("8"), settings.getSetting("baresip.call_max_calls"));
    }

    @Test
    void successfulApplyWritesLastgoodAfterCtrlTcpOk() throws IOException {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        facade.apply(Map.of("baresip.call_max_calls", "8"));

        Path lastgood = baresipDir.resolve("config.lastgood");

        assertTrue(Files.exists(lastgood), "proven-good config must be backed up");
        assertEquals(Files.readAllLines(baresipDir.resolve("config")), Files.readAllLines(lastgood),
                     "lastgood must snapshot the config that started ok");
        assertTrue(PrivateFiles.isOwnerOnly(lastgood), "lastgood must be owner-only (0600)");
    }

    @Test
    void failedStartRestoresLastgoodOnce() throws IOException {

        List<String> goodLines = List.of("# last known good", "call_max_calls\t3", "call_hold_other_calls\tno");

        Files.write(baresipDir.resolve("config.lastgood"), goodLines);

        FakeRestart restart = new FakeRestart(false, true);

        ApplyResult result = newService(restart).apply(Map.of("baresip.call_max_calls", "8"));

        assertEquals(ApplyResult.RESTORED_BACKUP, result, "user must be told the previous config was restored");
        assertEquals(2, restart.invocations, "exactly one restore restart, no retry loop");
        assertEquals(goodLines, Files.readAllLines(baresipDir.resolve("config")),
                     "config on disk must be the restored backup");
        assertEquals(goodLines, Files.readAllLines(baresipDir.resolve("config.lastgood")),
                     "a failed apply must not overwrite the backup");
    }

    @Test
    void restoredConfigAlsoFailsYieldsFailedWithoutRetryLoop() throws IOException {

        List<String> goodLines = List.of("# last known good", "call_hold_other_calls\tno");

        Files.write(baresipDir.resolve("config.lastgood"), goodLines);

        // third true would only be consumed by an illegal retry
        FakeRestart restart = new FakeRestart(false, false, true);

        ApplyResult result = newService(restart).apply(Map.of("baresip.call_max_calls", "8"));

        assertEquals(ApplyResult.FAILED, result);
        assertEquals(2, restart.invocations, "no retry loop after a failed restore");
        assertEquals(goodLines, Files.readAllLines(baresipDir.resolve("config")));
    }

    @Test
    void restoredBackupRevertsSettingsToLastGoodState() throws IOException {

        Files.write(baresipDir.resolve("config.lastgood"), List.of("# last known good", "call_hold_other_calls\tno"));

        settings.saveSetting("baresip.call_max_calls", "3");

        FakeRestart restart = new FakeRestart(false, true);

        ApplyResult result = newService(restart).apply(Map.of("baresip.call_max_calls", "8",
                                                              "baresip.call_local_timeout", "30"));

        assertEquals(ApplyResult.RESTORED_BACKUP, result);
        assertEquals(Optional.of("3"), settings.getSetting("baresip.call_max_calls"),
                     "the poisoned value must revert to the previous one");
        assertEquals(Optional.empty(), settings.getSetting("baresip.call_local_timeout"),
                     "a key with no previous value must be deleted");
    }

    @Test
    void failedApplyRevertsSettingsToLastGoodState() throws IOException {

        Files.write(baresipDir.resolve("config.lastgood"), List.of("# last known good", "call_hold_other_calls\tno"));

        settings.saveSetting("baresip.call_max_calls", "3");

        FakeRestart restart = new FakeRestart(false, false);

        ApplyResult result = newService(restart).apply(Map.of("baresip.call_max_calls", "8",
                                                              "baresip.call_local_timeout", "30"));

        assertEquals(ApplyResult.FAILED, result);
        assertEquals(Optional.of("3"), settings.getSetting("baresip.call_max_calls"),
                     "the poisoned value must revert to the previous one");
        assertEquals(Optional.empty(), settings.getSetting("baresip.call_local_timeout"),
                     "a key with no previous value must be deleted");
    }

    @Test
    void missingLastgoodRestoresMinimalFallback() throws IOException {

        FakeRestart restart = new FakeRestart(false, true);

        ApplyResult result = newService(restart).apply(Map.of("baresip.call_max_calls", "8"));

        assertEquals(ApplyResult.RESTORED_BACKUP, result);

        List<String> config = Files.readAllLines(baresipDir.resolve("config"));

        assertTrue(config.contains("module_app\t\tctrl_tcp.so"), "restore source must be the minimal fallback");
        assertTrue(config.contains("ctrl_tcp_listen\t\t127.0.0.1:4444"));
        assertEquals("call_hold_other_calls\tno", config.get(config.size() - 1),
                     "even the restored fallback ends with hold=no");
    }

    @Test
    void invalidValueFailsWithoutRestartOrPersist() {

        FakeRestart restart = new FakeRestart();

        ApplyResult result = newService(restart).apply(Map.of("baresip.call_max_calls", "-1"));

        assertEquals(ApplyResult.FAILED, result);
        assertEquals(0, restart.invocations, "invalid values must never restart baresip");
        assertFalse(settings.values.containsKey("baresip.call_max_calls"), "invalid values must not be persisted");
        assertFalse(Files.exists(baresipDir.resolve("config")), "invalid values must not produce a config");
    }

    @Test
    void unknownSettingsKeysAreIgnored() throws IOException {

        FakeRestart restart = new FakeRestart();

        ApplyResult result = newService(restart).apply(Map.of("baresip.not_a_real_option", "x"));

        assertEquals(ApplyResult.APPLIED, result);
        assertFalse(settings.values.containsKey("baresip.not_a_real_option"));
        assertFalse(Files.readAllLines(baresipDir.resolve("config")).stream()
                         .anyMatch(line -> line.contains("not_a_real_option")));
    }

    @Test
    void emptyRtpTimeoutIsEmittedAsZero() throws IOException {

        newService(new FakeRestart()).apply(Map.of("baresip.rtp_timeout", ""));

        List<String> config = Files.readAllLines(baresipDir.resolve("config"));

        assertTrue(config.contains("rtp_timeout\t0"), "baresip disables the timeout with 0");
        assertEquals(Optional.of(""), settings.getSetting("baresip.rtp_timeout"),
                     "the raw empty value stays persisted for the UI");
    }

    @Test
    void unsetOptionsFallBackToRegistryDefaults() throws IOException {

        newService(new FakeRestart()).apply(Map.of());

        List<String> config = Files.readAllLines(baresipDir.resolve("config"));

        assertTrue(config.contains("call_local_timeout\t120"));
        assertTrue(config.contains("rtp_timeout\t60"));
        assertTrue(config.contains("audio_buffer_mode\tfixed"));
    }

    @Test
    void applyUsesExistingAppConfigAsBase() throws IOException {

        Files.write(baresipDir.resolve("config"),
                    List.of("# user config copy", "call_max_calls 2", "custom_key custom_value"));

        newService(new FakeRestart()).apply(Map.of("baresip.call_max_calls", "8"));

        List<String> config = Files.readAllLines(baresipDir.resolve("config"));

        assertTrue(config.contains("call_max_calls\t8"));
        assertTrue(config.contains("custom_key custom_value"), "unmanaged user lines must survive the apply");
        assertFalse(config.stream().anyMatch(line -> line.equals("call_max_calls 2")));
    }

    @Test
    void vanishedDeviceFallsBackToDefault() throws IOException {

        lister.sinks = List.of("sink_a");

        newService(new FakeRestart()).apply(Map.of("baresip.audio_player", "pulse,gone_sink"));

        List<String> config = Files.readAllLines(baresipDir.resolve("config"));

        assertTrue(config.contains("audio_player\talsa,default"), "disappeared device must fall back to the default");
        assertFalse(config.stream().anyMatch(line -> line.contains("gone_sink")));
    }

    @Test
    void presentDeviceIsKept() throws IOException {

        lister.sinks = List.of("sink_a");

        newService(new FakeRestart()).apply(Map.of("baresip.audio_player", "pulse,sink_a"));

        assertTrue(Files.readAllLines(baresipDir.resolve("config")).contains("audio_player\tpulse,sink_a"));
    }

    @Test
    void deviceIsKeptWhenPactlUnavailable() throws IOException {

        // null list means pactl is missing, so revalidation is
        // impossible and the value must be kept as is
        lister.sinks = null;

        newService(new FakeRestart()).apply(Map.of("baresip.audio_player", "pulse,sink_a"));

        assertTrue(Files.readAllLines(baresipDir.resolve("config")).contains("audio_player\tpulse,sink_a"));
    }

    @Test
    void vanishedSourceFallsBackToDefault() throws IOException {

        lister.sources = List.of("mic_a");

        newService(new FakeRestart()).apply(Map.of("baresip.audio_source", "pulse,mic_b"));

        assertTrue(Files.readAllLines(baresipDir.resolve("config")).contains("audio_source\talsa,default"));
    }

    @Test
    void applyIsSerializedAcrossThreads() throws InterruptedException {

        FakeRestart restart = new FakeRestart();

        restart.delayMs = 5;

        BaresipSettingsFacade facade = newService(restart);

        Thread first = new Thread(() -> facade.apply(Map.of("baresip.call_max_calls", "8")));

        Thread second = new Thread(() -> facade.apply(Map.of("baresip.call_max_calls", "6")));

        first.start();
        second.start();
        first.join();
        second.join();

        assertEquals(2, restart.invocations);
        assertEquals(List.of("enter", "exit", "enter", "exit"), restart.events,
                     "a second apply must not interleave its restart");
    }

    @Test
    void previewShowsPatchedConfigWithoutSideEffects() {

        FakeRestart restart = new FakeRestart();

        String preview = newService(restart).previewPatchedConfig(Map.of("baresip.call_max_calls", "9"));

        assertTrue(preview.contains("call_max_calls\t9"));
        assertTrue(preview.endsWith("call_hold_other_calls\tno\n"), "preview must show the forced patch last");
        assertFalse(Files.exists(baresipDir.resolve("config")), "preview must never write the config");
        assertTrue(settings.values.isEmpty(), "preview must never persist");
        assertEquals(0, restart.invocations);
    }

    @Test
    void readBaseConfigLinesReturnsTheCurrentBase() throws IOException {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        // No config file yet: the minimal fallback is the base
        List<String> fallback = facade.readBaseConfigLines();

        assertTrue(fallback.contains("module_app\t\tctrl_tcp.so"),
                   "without a config file the minimal fallback is the base");

        // After an apply, the base lines are exactly the file on disk
        facade.apply(Map.of("baresip.call_max_calls", "8"));

        List<String> fromDisk = facade.readBaseConfigLines();

        assertEquals(Files.readAllLines(baresipDir.resolve("config")), fromDisk,
                     "the exposed base lines must mirror the config file");
    }

    @Test
    void previewOverCachedBaseLinesMatchesTheOneShotPreview() {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        List<String> cachedBase = facade.readBaseConfigLines();

        Map<String, String> pending = Map.of("baresip.call_max_calls", "9");

        assertEquals(facade.previewPatchedConfig(pending), facade.previewPatchedConfig(pending, cachedBase),
                     "patching cached base lines must equal patching a fresh read");

        assertFalse(Files.exists(baresipDir.resolve("config")), "preview must never write the config");
    }

    @Test
    void deviceListsDelegateToLister() {

        BaresipSettingsFacade facade = newService(new FakeRestart());

        lister.sinks = List.of("sink_a");
        lister.sources = List.of("mic_a");

        assertEquals(List.of("sink_a"), facade.listSinks());
        assertEquals(List.of("mic_a"), facade.listSources());

        lister.sinks = null;

        assertNull(facade.listSinks(), "degraded pactl must surface as null");
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
     * Restart fake: replays scripted outcomes, counts every
     * invocation and records enter/exit events so serialization
     * can be asserted. An empty outcome queue means success.
     */
    private static final class FakeRestart implements BaresipConfigService.RestartOperation {

        private final Deque<Boolean> outcomes = new ArrayDeque<>();

        private final List<String> events = Collections.synchronizedList(new ArrayList<>());

        private int invocations;

        private long delayMs;

        FakeRestart(boolean... scripted) {

            for (boolean outcome : scripted) {
                outcomes.add(outcome);
            }
        }

        @Override
        public boolean restart() {

            invocations++;

            events.add("enter");

            if (delayMs > 0) {

                try {

                    Thread.sleep(delayMs);

                } catch (InterruptedException exception) {

                    Thread.currentThread().interrupt();
                }
            }

            events.add("exit");

            Boolean outcome = outcomes.poll();

            return outcome == null || outcome;
        }
    }

    /**
     * Device lister stub: returns scripted sink/source lists,
     * null meaning pactl is unavailable.
     */
    private static final class StubDeviceLister extends PactlDeviceLister {

        private List<String> sinks = List.of();

        private List<String> sources = List.of();

        @Override
        public List<String> listSinks() {

            return sinks;
        }

        @Override
        public List<String> listSources() {

            return sources;
        }
    }
}
