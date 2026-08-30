package com.jsoftsip.core.settings.baresip;

import java.util.List;
import java.util.Map;

/**
 * Port through which the UI drives the baresip settings
 * pipeline. The real implementation lives in the native-bridge
 * module and is wired by the composition root. The MOCK backend
 * exposes no implementation, so callers must tolerate an absent
 * facade.
 *
 * <p>All methods that touch the baresip process are serialized
 * by the implementation: a second apply starts only after the
 * first apply and restart completed.
 */
public interface BaresipSettingsFacade {

    /**
     * Validates, persists and applies the given baresip settings,
     * then restarts the baresip process. Keys are settings-table
     * keys (baresip.<option>) and values are raw strings as typed
     * in the UI. On start failure the last known-good config is
     * restored once. There is no retry loop. On both failure
     * outcomes the persisted keys are reverted to their pre-apply
     * values, so a baresip-breaking value cannot re-break the
     * next launch.
     */
    ApplyResult apply(Map<String, String> values);

    /**
     * Renders the patched config baresip would receive for the
     * given pending values. Purely in memory: nothing is
     * persisted and nothing is written to disk.
     */
    String previewPatchedConfig(Map<String, String> pending);

    /**
     * Current base config lines the preview patches over: the app
     * config file when readable, otherwise the minimal fallback.
     * Callers that refresh a live preview (for example on every
     * settings edit) read this ONCE and pass the snapshot to
     * {@link #previewPatchedConfig(Map, List)}, so no per-keystroke
     * file access exists.
     */
    List<String> readBaseConfigLines();

    /**
     * Renders the patched config baresip would receive for the
     * given pending values, patching over the given base lines
     * instead of re-reading the config file. Purely in memory:
     * nothing is persisted and nothing is written to disk.
     */
    String previewPatchedConfig(Map<String, String> pending, List<String> baseLines);

    /**
     * Names of the output devices reported by pactl, or null
     * when pactl is unavailable. Callers fall back to a free
     * text field on null.
     */
    List<String> listSinks();

    /**
     * Names of the input devices reported by pactl, or null
     * when pactl is unavailable. Callers fall back to a free
     * text field on null.
     */
    List<String> listSources();

    /**
     * Outcome of an apply call. RESTORED_BACKUP means the new
     * config broke baresip and the previous known-good config
     * was restored. FAILED means either the values were invalid
     * or even the restored config failed to start.
     */
    enum ApplyResult {
        APPLIED, RESTORED_BACKUP, FAILED
    }
}
