package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.settings.SettingsService;
import com.jsoftsip.core.settings.baresip.BaresipOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the ordered patch list applied to every baresip config
 * the app writes, both at launch (BaresipLauncher) and on apply
 * (BaresipConfigService). Sharing this builder guarantees both
 * paths emit the exact same effective config.
 *
 * <p>The plan contains every registry option with its effective
 * value (pending override, then persisted setting, then template
 * default) followed by the immutable audio-safety patch as the
 * LAST entry, so call_hold_other_calls no always wins over any
 * user-config or database value.
 */
public final class BaresipPatchPlan {

    /**
     * Immutable audio-safety patch: without it baresip holds one
     * leg of concurrent calls with a sendonly re-INVITE, killing
     * audio. Never exposed in the UI or the settings table.
     */
    public static final ConfigPatch FORCED_HOLD_NO = new ConfigPatch("call_hold_other_calls", "no",
        "Forced by JSoftSIP: audio for concurrent calls");

    private BaresipPatchPlan() {
    }

    /**
     * Returns the full ordered patch list: one patch per registry
     * option with its effective value, then FORCED_HOLD_NO last.
     */
    public static List<ConfigPatch> patches(SettingsService settingsService, PactlDeviceLister deviceLister,
                                            Map<String, String> overrides) {

        List<ConfigPatch> patches = new ArrayList<>();

        for (BaresipOption option : BaresipOption.values()) {

            patches.add(new ConfigPatch(option.configKey(),
                effectiveValue(option, overrides, settingsService, deviceLister), null));
        }

        patches.add(FORCED_HOLD_NO);

        return patches;
    }

    private static String effectiveValue(BaresipOption option, Map<String, String> overrides,
                                         SettingsService settingsService, PactlDeviceLister deviceLister) {

        String raw = overrides.get(option.settingsKey());

        if (raw == null) {

            raw = settingsService.getSetting(option.settingsKey()).orElse(option.defaultValue());
        }

        String value = raw.trim();

        // baresip disables the rtp timeout with 0, an empty
        // field in the UI means disabled
        if (value.isEmpty()) {
            value = "0";
        }

        if (option.type() == BaresipOption.Type.DEVICE) {
            value = revalidateDevice(option, value, deviceLister);
        }

        return value;
    }

    /**
     * Device revalidation: when the selected device disappeared
     * from a fresh pactl listing, the template default is emitted
     * instead. A null listing means pactl is unavailable, so the
     * value is kept as is. The "default" pseudo device always
     * passes.
     */
    private static String revalidateDevice(BaresipOption option, String value, PactlDeviceLister deviceLister) {

        int comma = value.indexOf(',');

        String devicePart = comma >= 0 ? value.substring(comma + 1) : value;

        if ("default".equals(devicePart)) {
            return value;
        }

        List<String> devices = option == BaresipOption.AUDIO_SOURCE
            ? deviceLister.listSources()
            : deviceLister.listSinks();

        if (devices == null || devices.contains(devicePart)) {
            return value;
        }

        log(option.configKey() + ": device " + devicePart + " no longer exists, using default");

        return option.defaultValue();
    }

    private static void log(String message) {

        BaresipLog.info(message);
    }
}
