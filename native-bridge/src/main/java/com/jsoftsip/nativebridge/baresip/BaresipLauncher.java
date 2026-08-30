package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.config.ApplicationPaths;
import com.jsoftsip.core.config.PrivateFiles;
import com.jsoftsip.core.settings.SettingsKeys;
import com.jsoftsip.core.settings.SettingsService;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BaresipLauncher {

    private static final int MAX_WAIT_ATTEMPTS = 30;

    private static final int WAIT_INTERVAL_MS = 500;

    private final String ctrlTcpHost;

    private final int ctrlTcpPort;

    private final SettingsService settingsService;

    private final PactlDeviceLister deviceLister;

    private final int maxWaitAttempts;

    private final long waitIntervalMs;

    private final BaresipProcessManager processManager;

    private BaresipSipClient sipClient;

    private Path baresipDirectory;

    private String executable;

    public BaresipLauncher(String ctrlTcpHost, int ctrlTcpPort, SettingsService settingsService,
                           PactlDeviceLister deviceLister) {

        this(ctrlTcpHost, ctrlTcpPort, settingsService, deviceLister, new BaresipProcessManager());
    }

    /**
     * Test seam: the process manager is injectable so launch and
     * restart can be exercised without a real baresip process.
     */
    BaresipLauncher(String ctrlTcpHost, int ctrlTcpPort, SettingsService settingsService,
                    PactlDeviceLister deviceLister, BaresipProcessManager processManager) {

        this(ctrlTcpHost, ctrlTcpPort, settingsService, deviceLister, processManager, MAX_WAIT_ATTEMPTS,
             WAIT_INTERVAL_MS);
    }

    /**
     * Test seam with an injected ctrl_tcp wait budget: a
     * launch-failure test no longer pays the full
     * production 15 second window before asserting the process
     * was stopped instead of leaked.
     */
    BaresipLauncher(String ctrlTcpHost, int ctrlTcpPort, SettingsService settingsService,
                    PactlDeviceLister deviceLister, BaresipProcessManager processManager, int maxWaitAttempts,
                    long waitIntervalMs) {

        this.ctrlTcpHost = ctrlTcpHost;

        this.ctrlTcpPort = ctrlTcpPort;

        this.settingsService = settingsService;

        this.deviceLister = deviceLister;

        this.processManager = processManager;

        this.maxWaitAttempts = maxWaitAttempts;

        this.waitIntervalMs = waitIntervalMs;
    }

    public void launch(Path baresipDirectory, String executable) throws IOException {

        // Remove any stale accounts file from the previous design
        // (accounts are provisioned via ctrl_tcp only now). If
        // Baresip found it with -f it would double-register.
        Files.deleteIfExists(baresipDirectory.resolve("accounts"));

        writeConfig(baresipDirectory);

        this.baresipDirectory = baresipDirectory;

        this.executable = executable;

        syncLogFileFlag();

        processManager.start(baresipDirectory, executable);

        try {

            waitForCtrlTcp();

        } catch (RuntimeException exception) {

            // A process that never answers ctrl_tcp must not be
            // left running orphaned after the failed launch
            processManager.stop();

            throw exception;
        }

        // The launch proved the current config bootable, so it
        // becomes the last known-good backup. This guarantees a
        // restore source exists before the first settings apply.
        PrivateFiles.copy(baresipDirectory.resolve("config"), baresipDirectory.resolve("config.lastgood"));
    }

    /**
     * Restarts baresip with the config currently on disk and
     * reports whether ctrl_tcp answered within the wait window.
     * The config itself is NOT touched here: callers write the
     * config they want tested before restarting, which is what
     * lets a restored backup take effect. IO failures surface as
     * false, never as exceptions.
     */
    public boolean restart() {

        if (baresipDirectory == null) {

            throw new IllegalStateException("launch must be called before restart");
        }

        processManager.stop();

        syncLogFileFlag();

        try {

            processManager.start(baresipDirectory, executable);

        } catch (IOException exception) {

            BaresipLog.error("Restart failed to start baresip", exception);

            return false;
        }

        return awaitCtrlTcp();
    }

    /**
     * Writes the Baresip config the app runs with. The user's real
     * config (~/.baresip/config) is read on every launch and
     * overwrites any previous config in the app directory, so
     * Baresip always runs with the same modules and settings the
     * user has configured. On that base the aubridge, ctrl_tcp and
     * jvidisp modules are ensured active before patching: without
     * them concurrent-call audio, the control channel and the
     * video frame transport silently die. If no user config
     * exists, the minimal fallback config is the patch base
     * instead. Both bases are patched with the shared patch plan:
     * persisted settings, template defaults, the video transport
     * endpoint first and the forced audio-safety patch last.
     */
    void writeConfig(Path baresipDirectory) throws IOException {

        Files.createDirectories(baresipDirectory);

        Path userConfig = Path.of(System.getProperty("user.home"), ".baresip", "config");

        List<String> base;

        if (Files.isReadable(userConfig)) {

            base = ensureModule(Files.readAllLines(userConfig), "aubridge.so", "# Added by JSoftSIP: enables audio for",
                                "# concurrent calls (outgoing + incoming).");

            base = ensureModule(base, "ctrl_tcp.so", "# Added by JSoftSIP: enables the ctrl_tcp control",
                                "# channel for app-managed accounts and calls.");

            base = ensureModule(base, "jvidisp.so", "# Added by JSoftSIP: enables video for",
                                "# incoming and outgoing calls.");

        } else {

            base = MinimalBaresipConfig.lines(ctrlTcpHost, ctrlTcpPort);
        }

        PrivateFiles.write(baresipDirectory.resolve("config"), BaresipConfigPatcher.apply(base, patchPlan()));
    }

    /**
     * Builds the full ordered patch list for the app config: the
     * video frame transport endpoint first (inert when jvidisp is
     * not loaded), then the shared plan whose last entry is the
     * immutable audio-safety patch, so call_hold_other_calls no
     * always wins over any user-config or database value.
     */
    private List<ConfigPatch> patchPlan() {

        List<ConfigPatch> patches = new ArrayList<>();

        patches.add(new ConfigPatch("video_tcp_listen", videoTcpEndpoint(),
            "# Video frame transport endpoint (JSoftSIP)"));

        patches.addAll(BaresipPatchPlan.patches(settingsService, deviceLister, Map.of()));

        return patches;
    }

    /**
     * Resolves the video frame transport endpoint from the
     * persisted settings, falling back to the built-in defaults
     * when the keys are absent.
     */
    private String videoTcpEndpoint() {

        String host = settingsService.getSetting(SettingsKeys.BARESIP_VIDEO_TCP_HOST)
                                     .orElse(SettingsKeys.BARESIP_VIDEO_TCP_HOST_DEFAULT);

        String port = settingsService.getSetting(SettingsKeys.BARESIP_VIDEO_TCP_PORT)
                                     .orElse(SettingsKeys.BARESIP_VIDEO_TCP_PORT_DEFAULT);

        return host + ":" + port;
    }

    /**
     * Ensures the given module is enabled in the given config
     * lines. Without aubridge Baresip only routes audio for one
     * call per process, so concurrent calls (e.g. an outgoing and
     * an incoming leg of the same instance) silently lose audio.
     * Without ctrl_tcp Baresip opens no control socket, so the app
     * cannot provision accounts and launch fails its wait. A
     * module is added only when it is not already loaded: a
     * commented "module <name>" line is uncommented in place,
     * otherwise the line is appended after the given explanatory
     * comments. The config in the app directory is rewritten from
     * the user config on every launch, so this operation is
     * idempotent.
     */
    private List<String> ensureModule(List<String> lines, String moduleName, String... comments) {

        Pattern activeLine = Pattern.compile("^\\s*module\\s+.*" + Pattern.quote(moduleName) + "(\\s+.*)?$");

        boolean alreadyEnabled = lines.stream().anyMatch(line -> activeLine.matcher(line).matches());

        if (alreadyEnabled) {
            return lines;
        }

        List<String> result = new ArrayList<>(lines);

        Pattern commentedLine = Pattern.compile("^\\s*#\\s*module\\s+.*" + Pattern.quote(moduleName) + "(\\s+.*)?$");

        for (int index = 0; index < result.size(); index++) {

            String line = result.get(index);

            if (commentedLine.matcher(line).matches()) {

                result.set(index, line.replaceFirst("#\\s*", ""));

                return result;
            }
        }

        for (String comment : comments) {
            result.add(comment);
        }

        result.add("module " + moduleName);

        return result;
    }

    /**
     * Wires the ctrl_tcp owner so the ordered shutdown can
     * disconnect the socket before the process is killed.
     * Called by BaresipSessionRestart at composition time,
     * which is the only production object holding both the
     * launcher and the sip client.
     */
    /**
     * Exposes the owned process manager for the composition root
     * to wire the crash-recovery supervisor. Deliberately public:
     * the accessor consumer lives in another module, so a
     * package-private seam cannot reach it.
     */
    public BaresipProcessManager getProcessManager() {

        return processManager;
    }

    void attachSipClient(BaresipSipClient sipClient) {

        this.sipClient = sipClient;
    }

    public void shutdown() {

        // Disconnect ctrl_tcp first: killing the process closes
        // the socket under the reader, and an intentional close
        // must not be reported as a reader failure
        if (sipClient != null) {

            sipClient.shutdown();
        }

        processManager.stop();
    }

    public boolean isRunning() {

        return processManager.isRunning();
    }

    /**
     * Reads the ui.logging.save_to_file preference on every
     * launch/restart and attaches or detaches the baresip and
     * jsoftsip file appenders accordingly, so the preference
     * applies to both log files without restarting the app. A
     * log directory that cannot be created degrades to
     * console-only logging.
     */
    private void syncLogFileFlag() {

        boolean saveToFile = Boolean.parseBoolean(settingsService.getSetting(SettingsKeys.UI_LOGGING_SAVE_TO_FILE)
                                                                 .orElse(SettingsKeys.UI_LOGGING_SAVE_TO_FILE_DEFAULT));

        if (!saveToFile) {

            BaresipLogConfig.detachFileAppender();
            BaresipLogConfig.detachJSoftSipFileAppender();

            return;
        }

        try {

            Path logDirectory = ApplicationPaths.getConfigDirectory().resolve("logs");

            BaresipLogConfig.attachFileAppender(logDirectory);

            BaresipLogConfig.attachJSoftSipFileAppender(logDirectory);

        } catch (IOException exception) {

            BaresipLog.warn("File logging unavailable", exception);
        }
    }

    private void waitForCtrlTcp() {

        if (!awaitCtrlTcp()) {

            throw new RuntimeException(
                "Baresip did not start within " + (maxWaitAttempts * waitIntervalMs / 1000) + " seconds");
        }
    }

    private boolean awaitCtrlTcp() {

        for (int attempt = 0; attempt < maxWaitAttempts; attempt++) {

            try (Socket ignored = new Socket(ctrlTcpHost, ctrlTcpPort)) {

                return true;

            } catch (IOException ignored) {
                // not ready yet
            }

            try {

                Thread.sleep(waitIntervalMs);

            } catch (InterruptedException exception) {

                Thread.currentThread().interrupt();

                log("Interrupted while waiting for Baresip ctrl_tcp");

                return false;
            }
        }

        return false;
    }

    private static void log(String message) {

        BaresipLog.info(message);
    }
}
