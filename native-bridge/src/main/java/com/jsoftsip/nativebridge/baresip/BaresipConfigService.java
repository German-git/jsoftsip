package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.config.PrivateFiles;
import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipOption;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the baresip settings apply flow: validate against
 * the BaresipOption registry, persist to the settings table,
 * regenerate the patched config, restart baresip and roll back
 * on start failure. Apply calls are synchronized, so a second
 * save only starts after the first apply and restart completed.
 *
 * <p>The backup config.lastgood is written ONLY after a restart
 * whose ctrl_tcp wait succeeded, which guarantees the backup is
 * always a bootable config. On start failure the backup is
     * restored exactly once. When the restored config also fails,
 * FAILED is returned and no retry loop runs. Without a backup
 * (first run), the minimal fallback config is the restore
 * source.
 */
public class BaresipConfigService implements BaresipSettingsFacade {

    /**
     * Restarts baresip and reports whether ctrl_tcp answered
     * within the existing wait window. Implementations must
     * swallow their own IO failures and report them as false.
     */
    public interface RestartOperation {

        boolean restart();
    }

    private final Path baresipDirectory;

    private final SettingsService settingsService;

    private final RestartOperation restartOperation;

    private final PactlDeviceLister deviceLister;

    private final String ctrlTcpHost;

    private final int ctrlTcpPort;

    public BaresipConfigService(Path baresipDirectory, SettingsService settingsService,
                                RestartOperation restartOperation, PactlDeviceLister deviceLister, String ctrlTcpHost,
                                int ctrlTcpPort) {

        this.baresipDirectory = baresipDirectory;
        this.settingsService = settingsService;
        this.restartOperation = restartOperation;
        this.deviceLister = deviceLister;
        this.ctrlTcpHost = ctrlTcpHost;
        this.ctrlTcpPort = ctrlTcpPort;
    }

    @Override
    public synchronized ApplyResult apply(Map<String, String> values) {

        for (Map.Entry<String, String> entry : values.entrySet()) {

            BaresipOption option = optionFor(entry.getKey());

            if (option == null) {
                continue;
            }

            String error = option.validate(entry.getValue());

            if (error != null) {

                log("Apply rejected: " + error);

                return ApplyResult.FAILED;
            }
        }

        Map<String, Optional<String>> snapshot = new HashMap<>();

        for (Map.Entry<String, String> entry : values.entrySet()) {

            if (optionFor(entry.getKey()) != null) {

                snapshot.put(entry.getKey(), settingsService.getSetting(entry.getKey()));

                settingsService.saveSetting(entry.getKey(), entry.getValue());
            }
        }

        Path configFile = baresipDirectory.resolve("config");

        try {

            Files.createDirectories(baresipDirectory);

            writeAtomic(configFile, patchedLines(values, readBaseLines()));

            if (restartOperation.restart()) {

                PrivateFiles.copy(configFile, baresipDirectory.resolve("config.lastgood"));

                log("Settings applied, config.lastgood updated");

                return ApplyResult.APPLIED;
            }

            ApplyResult outcome = restoreOnce(configFile);

            revertSettings(snapshot);

            return outcome;

        } catch (IOException exception) {

            BaresipLog.error("Apply failed", exception);

            revertSettings(snapshot);

            return ApplyResult.FAILED;
        }
    }

    @Override
    public String previewPatchedConfig(Map<String, String> pending) {

        List<String> patched = patchedLines(pending, readBaseLines());

        return String.join("\n", patched) + "\n";
    }

    @Override
    public List<String> readBaseConfigLines() {

        return readBaseLines();
    }

    @Override
    public String previewPatchedConfig(Map<String, String> pending, List<String> baseLines) {

        List<String> patched = patchedLines(pending, baseLines);

        return String.join("\n", patched) + "\n";
    }

    @Override
    public List<String> listSinks() {

        return deviceLister.listSinks();
    }

    @Override
    public List<String> listSources() {

        return deviceLister.listSources();
    }

    /**
     * Writes the pre-apply snapshot back so a value that passed
     * validation but broke baresip cannot re-break the next
     * launch. Keys that had no previous value are deleted.
     */
    private void revertSettings(Map<String, Optional<String>> snapshot) {

        for (Map.Entry<String, Optional<String>> entry : snapshot.entrySet()) {

            if (entry.getValue().isPresent()) {

                settingsService.saveSetting(entry.getKey(), entry.getValue().get());

            } else {

                settingsService.deleteSetting(entry.getKey());
            }
        }

        log("Persisted settings reverted to the previous state");
    }

    /**
     * Single restore attempt: copy the last known-good config
     * back (or regenerate the minimal fallback when no backup
     * exists yet) and restart once. A second failure surfaces
     * FAILED, never another attempt.
     */
    private ApplyResult restoreOnce(Path configFile) throws IOException {

        Path lastgood = baresipDirectory.resolve("config.lastgood");

        if (Files.isReadable(lastgood)) {

            writeAtomic(configFile, Files.readAllLines(lastgood));

        } else {

            writeAtomic(configFile, BaresipConfigPatcher.apply(MinimalBaresipConfig.lines(ctrlTcpHost, ctrlTcpPort),
                                                               List.of(BaresipPatchPlan.FORCED_HOLD_NO)));
        }

        if (restartOperation.restart()) {

            BaresipLog.error("Start failed, previous config restored");

            return ApplyResult.RESTORED_BACKUP;
        }

        BaresipLog.error("Start failed and restored config also fails");

        return ApplyResult.FAILED;
    }

    /**
     * Patches the base lines with the shared patch plan: every
     * registry option with its effective value (pending map,
     * then persisted setting, then template default), then the
     * forced audio-safety patch last so it always wins.
     */
    private List<String> patchedLines(Map<String, String> values, List<String> base) {

        return BaresipConfigPatcher.apply(base, BaresipPatchPlan.patches(settingsService, deviceLister, values));
    }

    /**
     * Current app config when present, otherwise the minimal
     * fallback. The app config was already copied from the user
     * config at launch, so patching it again is idempotent.
     */
    private List<String> readBaseLines() {

        Path configFile = baresipDirectory.resolve("config");

        if (Files.isReadable(configFile)) {

            try {

                return Files.readAllLines(configFile);

            } catch (IOException exception) {

                throw new UncheckedIOException(exception);
            }
        }

        return MinimalBaresipConfig.lines(ctrlTcpHost, ctrlTcpPort);
    }

    /**
     * Writes the config atomically by first writing to a sibling temp file
     * and then moving it over the target. A crash during the write leaves
     * the original config intact, so baresip cannot be left with a
     * truncated config.
     */
    private void writeAtomic(Path target, List<String> lines) throws IOException {

        Path temp = target.resolveSibling(target.getFileName() + ".tmp");

        PrivateFiles.write(temp, lines);

        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static BaresipOption optionFor(String settingsKey) {

        for (BaresipOption option : BaresipOption.values()) {

            if (option.settingsKey().equals(settingsKey)) {
                return option;
            }
        }

        return null;
    }

    private static void log(String message) {

        BaresipLog.info(message);
    }
}
