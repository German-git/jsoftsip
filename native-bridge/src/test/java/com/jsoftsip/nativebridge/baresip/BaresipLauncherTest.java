package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.config.PrivateFiles;
import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.core.settings.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipLauncherTest {

    private static final String CTRL_HOST = "127.0.0.1";

    @TempDir
    Path fakeHome;

    @TempDir
    Path baresipDir;

    private String originalUserHome;

    private InMemorySettings settings;

    private StubDeviceLister lister;

    private FakeProcessManager processManager;

    private ServerSocket ctrlTcpServer;

    @BeforeEach
    void setUp() {

        originalUserHome = System.getProperty("user.home");

        System.setProperty("user.home", fakeHome.toString());

        settings = new InMemorySettings();

        lister = new StubDeviceLister();

        processManager = new FakeProcessManager();
    }

    @AfterEach
    void tearDown() throws IOException {

        System.setProperty("user.home", originalUserHome);

        BaresipLogConfig.detachFileAppender();

        if (ctrlTcpServer != null) {
            ctrlTcpServer.close();
        }
    }

    @Test
    void userConfigIsCopiedAndPatchedWithDatabaseValues() throws IOException {

        writeUserConfig("call_max_calls 2", "some_custom_key custom");

        settings.saveSetting("baresip.call_max_calls", "8");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("call_max_calls\t8"), "the persisted value must win over the user config");
        assertFalse(config.contains("call_max_calls 2"), "the stale user value must be replaced");
        assertTrue(config.contains("some_custom_key custom"), "unmanaged user lines must survive patching");
    }

    @Test
    void forcedHoldNoWinsOverUserConfig() throws IOException {

        writeUserConfig("call_hold_other_calls yes");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("call_hold_other_calls\tno"), "the audio-safety patch must be present");
        assertFalse(config.stream().anyMatch(line -> line.matches("^\\s*#?\\s*call_hold_other_calls" + "\\s+yes.*$")),
                    "no active or commented yes line may survive");
    }

    @Test
    void writeConfigProducesOwnerOnlyConfigFile() throws IOException {

        newLauncher(4444).writeConfig(baresipDir);

        assertTrue(PrivateFiles.isOwnerOnly(baresipDir.resolve("config")), "the app config must be owner-only (0600)");
    }

    @Test
    void enabledAubridgeModuleIsKeptUntouched() throws IOException {

        // ctrl_tcp and jvidisp are ensured too, so the fixture
        // must already load them or their comment blocks would be
        // appended
        writeUserConfig("module aubridge.so", "module ctrl_tcp.so", "module jvidisp.so", "module g711.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        long aubridgeLines = config.stream()
                                   .filter(line -> line.matches("^\\s*module\\s+aubridge\\.so" + "\\s*(#.*)?$"))
                                   .count();

        assertEquals(1, aubridgeLines, "an enabled aubridge must not be duplicated");
        assertFalse(config.stream().anyMatch(line -> line.contains("# Added by JSoftSIP")),
                    "no append comment block is expected");
    }

    @Test
    void commentedAubridgeModuleIsUncommented() throws IOException {

        // ctrl_tcp and jvidisp are ensured too, so the fixture
        // must already load them or their comment blocks would be
        // appended
        writeUserConfig("#module aubridge.so", "module ctrl_tcp.so", "module jvidisp.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module aubridge.so"),
                   "a commented aubridge line must be uncommented" + " in place");
        assertFalse(config.stream().anyMatch(line -> line.contains("# Added by JSoftSIP")),
                    "no append comment block is expected");
    }

    @Test
    void missingAubridgeModuleIsAppendedWithComment() throws IOException {

        writeUserConfig("module g711.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module aubridge.so"), "aubridge must be appended when absent");
        assertTrue(config.contains("# Added by JSoftSIP: enables audio for"),
                   "the explanatory comment block must be kept");
    }

    @Test
    void missingJvidispModuleIsAppendedWithComments() throws IOException {

        writeUserConfig("module aubridge.so", "module ctrl_tcp.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module jvidisp.so"), "jvidisp must be appended when absent");
        assertTrue(config.contains("# Added by JSoftSIP: enables video for"),
                   "the jvidisp comment block must precede the module");
        assertTrue(config.contains("# incoming and outgoing calls."), "the jvidisp comment block must be complete");
    }

    @Test
    void videoTcpListenUsesPersistedEndpoint() throws IOException {

        settings.saveSetting(SettingsKeys.BARESIP_VIDEO_TCP_HOST, "127.0.0.1");
        settings.saveSetting(SettingsKeys.BARESIP_VIDEO_TCP_PORT, "4446");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("video_tcp_listen\t127.0.0.1:4446"),
                   "the video transport must listen on the persisted" + " endpoint");
    }

    @Test
    void videoTcpListenFallsBackToDefaultEndpoint() throws IOException {

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("video_tcp_listen\t127.0.0.1:4445"),
                   "the video transport must fall back to the default" + " endpoint");
    }

    @Test
    void forcedHoldNoRemainsTheLastConfigLine() throws IOException {

        writeUserConfig("module aubridge.so", "module ctrl_tcp.so", "module jvidisp.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertEquals("call_hold_other_calls\tno", config.get(config.size() - 1),
                     "the audio-safety patch must stay the last line");
    }

    @Test
    void minimalFallbackIsPatchedWhenNoUserConfig() throws IOException {

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module_app\t\tctrl_tcp.so"), "the minimal fallback must be the patch base");
        assertTrue(config.contains("ctrl_tcp_listen\t\t127.0.0.1:4444"), "the fallback must expose ctrl_tcp");
        assertEquals("call_hold_other_calls\tno", config.get(config.size() - 1),
                     "the forced audio-safety patch is appended last");
        assertFalse(config.contains("module aubridge.so"),
                    "aubridge is only ensured on the copied user" + " config, preserving launcher behavior");
    }

    @Test
    void enabledCtrlTcpModuleIsKeptUntouched() throws IOException {

        writeUserConfig("module ctrl_tcp.so", "module aubridge.so", "module jvidisp.so", "module g711.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        long ctrlTcpLines = config.stream()
                                  .filter(line -> line.matches("^\\s*module\\s+.*ctrl_tcp\\.so" + "(\\s+.*)?$"))
                                  .count();

        assertEquals(1, ctrlTcpLines, "an enabled ctrl_tcp must not be duplicated");
        assertFalse(config.stream().anyMatch(line -> line.contains("# Added by JSoftSIP")),
                    "no append comment block is expected");
    }

    @Test
    void enabledCtrlTcpModuleWithAbsolutePathIsKeptUntouched() throws IOException {

        writeUserConfig("module /usr/lib/x86_64-linux-gnu/baresip/" + "modules/ctrl_tcp.so", "module aubridge.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module /usr/lib/x86_64-linux-gnu/baresip/" + "modules/ctrl_tcp.so"),
                   "an active ctrl_tcp with an absolute path must" + " survive untouched");
        assertFalse(config.contains("module ctrl_tcp.so"),
                    "no plain module line may be appended when the" + " path-based one is already active");
    }

    @Test
    void commentedCtrlTcpModuleIsUncommented() throws IOException {

        writeUserConfig("#module ctrl_tcp.so", "module aubridge.so", "module jvidisp.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module ctrl_tcp.so"),
                   "a commented ctrl_tcp line must be uncommented" + " in place");
        assertFalse(config.stream().anyMatch(line -> line.contains("# Added by JSoftSIP")),
                    "no append comment block is expected");
    }

    @Test
    void commentedCtrlTcpModuleWithPathAndArgsIsUncommented() throws IOException {

        writeUserConfig("#module /usr/lib/baresip/modules/ctrl_tcp.so 4444", "module aubridge.so", "module jvidisp.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module /usr/lib/baresip/modules/" + "ctrl_tcp.so 4444"),
                   "uncommenting must preserve the module path and" + " its arguments");
        assertEquals(1, config.stream().filter(line -> line.matches("^\\s*module\\s+.*ctrl_tcp\\.so" + "(\\s+.*)?$"))
                              .count(),
                     "the uncommented line must not be duplicated");
    }

    @Test
    void missingCtrlTcpModuleIsAppendedWithComments() throws IOException {

        // ctrl_tcp_listen carries the same endpoint the fallback
        // would use, so the port the app waits on stays intact
        writeUserConfig("module aubridge.so", "module g711.so", "ctrl_tcp_listen 127.0.0.1:4444");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        int ctrlTcpIndex = config.indexOf("module ctrl_tcp.so");

        assertTrue(ctrlTcpIndex >= 0, "ctrl_tcp must be appended when absent");
        assertEquals("# Added by JSoftSIP: enables the ctrl_tcp control", config.get(ctrlTcpIndex - 2),
                     "the explanatory comment block must precede the" + " module line");
        assertEquals("# channel for app-managed accounts and calls.", config.get(ctrlTcpIndex - 1),
                     "the explanatory comment block must precede the" + " module line");
        assertTrue(ctrlTcpIndex > config.indexOf("module g711.so"), "the module line must be appended at the end");
        assertTrue(config.contains("ctrl_tcp_listen 127.0.0.1:4444"), "the listen endpoint must survive patching");
    }

    @Test
    void writeConfigTwiceProducesTheSameConfig() throws IOException {

        writeUserConfig("#module ctrl_tcp.so", "module aubridge.so");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> first = readAppConfig();

        newLauncher(4444).writeConfig(baresipDir);

        List<String> second = readAppConfig();

        assertEquals(first, second, "regenerating the config must be idempotent");
        assertEquals(1, second.stream().filter(line -> line.matches("^\\s*module\\s+.*ctrl_tcp\\.so" + "(\\s+.*)?$"))
                              .count(),
                     "ctrl_tcp must not be duplicated across runs");
    }

    @Test
    void ctrlTcpEnsureLeavesOtherLinesUntouched() throws IOException {

        writeUserConfig("module aubridge.so", "module g711.so", "module jvidisp.so", "#module ctrl_tcp.so",
                        "some_custom_key custom");

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertEquals(1, config.stream().filter(line -> line.matches("^\\s*module\\s+.*aubridge\\.so" + "(\\s+.*)?$"))
                              .count(),
                     "the aubridge line must survive untouched");
        assertEquals(1,
                     config.stream().filter(line -> line.matches("^\\s*module\\s+.*g711\\.so" + "(\\s+.*)?$")).count(),
                     "other module lines must survive untouched");
        assertTrue(config.contains("some_custom_key custom"), "unmanaged option lines must survive patching");
        assertFalse(config.stream().anyMatch(line -> line.contains("# Added by JSoftSIP")),
                    "no append comment block is expected when every" + " module is present");
    }

    @Test
    void minimalFallbackProducesConfigWithCtrlTcpPresent() throws IOException {

        newLauncher(4444).writeConfig(baresipDir);

        List<String> config = readAppConfig();

        assertTrue(config.contains("module_app\t\tctrl_tcp.so"), "the fallback must load ctrl_tcp");
        assertTrue(config.contains("ctrl_tcp_listen\t\t127.0.0.1:4444"), "the fallback must expose the launcher port");
        assertFalse(config.stream().anyMatch(line -> line.matches("^\\s*module\\s+.*ctrl_tcp\\.so" + "(\\s+.*)?$")),
                    "the fallback loads ctrl_tcp via module_app, so" + " no module line may be appended");
    }

    @Test
    void successfulLaunchWritesLastgoodBackup() throws Exception {

        startCtrlTcpServer();

        newLauncher(ctrlTcpServer.getLocalPort()).launch(baresipDir, "baresip");

        Path config = baresipDir.resolve("config");
        Path lastgood = baresipDir.resolve("config.lastgood");

        assertTrue(Files.exists(lastgood), "a proven-good backup must exist right after" + " a successful launch");
        assertEquals(Files.readAllLines(config), Files.readAllLines(lastgood),
                     "the backup must match the config that started" + " baresip");
        assertTrue(PrivateFiles.isOwnerOnly(lastgood), "the lastgood backup must be owner-only (0600)");
        assertEquals(List.of("start"), processManager.events, "launch must start the process exactly once");
    }

    @Test
    void restartStopsStartsAndReportsCtrlTcpSuccess() throws Exception {

        startCtrlTcpServer();

        BaresipLauncher launcher = newLauncher(ctrlTcpServer.getLocalPort());

        launcher.launch(baresipDir, "baresip");

        processManager.events.clear();

        assertTrue(launcher.restart());
        assertEquals(List.of("stop", "start"), processManager.events, "restart must stop before starting again");
    }

    @Test
    void restartReportsFalseWhenProcessFailsToStart() throws Exception {

        startCtrlTcpServer();

        BaresipLauncher launcher = newLauncher(ctrlTcpServer.getLocalPort());

        launcher.launch(baresipDir, "baresip");

        processManager.failOnStart = true;

        assertFalse(launcher.restart(), "an IO failure on start must surface as false");
    }

    @Test
    void restartBeforeLaunchThrows() {

        BaresipLauncher launcher = newLauncher(4444);

        assertThrows(IllegalStateException.class, launcher::restart,
                     "restart needs the directory and executable" + " captured by a previous launch");
    }

    @Test
    void logFileAppenderNotAttachedWhenSaveToFileUnset() throws Exception {

        startCtrlTcpServer();

        newLauncher(ctrlTcpServer.getLocalPort()).launch(baresipDir, "baresip");

        assertFalse(BaresipLogConfig.isFileAppenderAttached(),
                    "the unset flag must keep the file appender" + " detached");
    }

    @Test
    void logFileAppenderAttachedWhenSaveToFileEnabled() throws Exception {

        startCtrlTcpServer();

        settings.saveSetting(SettingsKeys.UI_LOGGING_SAVE_TO_FILE, "true");

        newLauncher(ctrlTcpServer.getLocalPort()).launch(baresipDir, "baresip");

        assertTrue(BaresipLogConfig.isFileAppenderAttached(), "the enabled flag must attach the file appender");
    }

    @Test
    void restartResyncsFileAppenderPerFlag() throws Exception {

        startCtrlTcpServer();

        settings.saveSetting(SettingsKeys.UI_LOGGING_SAVE_TO_FILE, "true");

        BaresipLauncher launcher = newLauncher(ctrlTcpServer.getLocalPort());

        launcher.launch(baresipDir, "baresip");

        settings.saveSetting(SettingsKeys.UI_LOGGING_SAVE_TO_FILE, "false");

        launcher.restart();

        assertFalse(BaresipLogConfig.isFileAppenderAttached(), "restart must detach when the flag turned off");
    }

    @Test
    void logFileKeyDefaultsToFalse() {

        assertEquals("ui.logging.save_to_file", SettingsKeys.UI_LOGGING_SAVE_TO_FILE,
                     "the key must be stable and namespaced");

        assertEquals("false", SettingsKeys.UI_LOGGING_SAVE_TO_FILE_DEFAULT, "the default must keep file logging off");
    }

    @Test
    void shutdownDisconnectsCtrlTcpBeforeStoppingTheProcess() {

        BaresipLauncher launcher = newLauncher(1);

        FakeCtrlConnection ctrl = new FakeCtrlConnection();

        BaresipSipClient client = new BaresipSipClient(ctrl);

        client.initialize();

        launcher.attachSipClient(client);

        launcher.shutdown();

        assertFalse(ctrl.isConnected(),
                    "the ordered shutdown must disconnect ctrl_tcp" + " before the process is killed");

        assertEquals(List.of("stop"), processManager.events, "the process must still be stopped");
    }

    @Test
    void launchTimeoutStopsTheProcessInsteadOfLeakingIt() throws Exception {

        Path script = executableScript("#!/bin/sh\nwhile true; do sleep 1; done\n");

        BaresipProcessManager realManager = new BaresipProcessManager();

        // The ctrl_tcp wait budget is injectable,
        // so this regression test runs in milliseconds instead of
        // paying the full production window.
        BaresipLauncher launcher = new BaresipLauncher(CTRL_HOST, unusedPort(), settings, lister, realManager, 3, 50);

        assertThrows(RuntimeException.class, () -> launcher.launch(baresipDir, script.toString()),
                     "a process that never answers ctrl_tcp must fail the launch");

        assertFalse(realManager.isRunning(), "a failed launch must not leak the baresip process");
    }

    private Path executableScript(String body) throws IOException {

        Path script = baresipDir.resolve("fake-baresip.sh");

        Files.writeString(script, body);

        Files.setPosixFilePermissions(script,
                                      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                 PosixFilePermission.OWNER_EXECUTE));

        return script;
    }

    private static int unusedPort() throws IOException {

        try (ServerSocket socket = new ServerSocket(0)) {

            return socket.getLocalPort();
        }
    }

    private BaresipLauncher newLauncher(int ctrlTcpPort) {

        return new BaresipLauncher(CTRL_HOST, ctrlTcpPort, settings, lister, processManager);
    }

    private void startCtrlTcpServer() throws IOException {

        ctrlTcpServer = new ServerSocket(0);
    }

    private void writeUserConfig(String... lines) throws IOException {

        Path configDir = fakeHome.resolve(".baresip");

        Files.createDirectories(configDir);

        Files.write(configDir.resolve("config"), List.of(lines));
    }

    private List<String> readAppConfig() throws IOException {

        return Files.readAllLines(baresipDir.resolve("config"));
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

    /**
     * Process manager fake: records stop/start events and never
     * spawns a real baresip process. failOnStart simulates an
     * executable that cannot be launched at all.
     */
    private static final class FakeProcessManager extends BaresipProcessManager {

        private final List<String> events = new ArrayList<>();

        private boolean failOnStart;

        @Override
        public synchronized void start(Path workingDirectory, String executable) throws IOException {

            if (failOnStart) {
                throw new IOException("boom");
            }

            events.add("start");
        }

        @Override
        public synchronized void stop() {

            events.add("stop");
        }
    }
}
