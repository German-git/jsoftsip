package com.jsoftsip.ui.baresip;

import com.jsoftsip.core.settings.baresip.BaresipOption;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * JavaFX-free view model behind the baresip section of the
 * settings dialog. Holds the pending raw value of every registry
 * option, seeds them from the persisted settings (falling back
 * to the template defaults) and validates against the registry,
 * so the controller only has to bind widgets to it.
 *
 * <p>Keeping this free of JavaFX imports is deliberate: the
 * dialog has no headless test infrastructure, so all validation
 * and mapping logic lives here where plain unit tests reach it.
 */
public final class BaresipSettingsFormModel {

    // Nine audio-side options grouped on the Audio tab:
    // device selection plus the buffer, silence and jitter controls
    private static final List<BaresipOption> AUDIO_TIER = List.of(BaresipOption.AUDIO_PLAYER,
                                                                  BaresipOption.AUDIO_SOURCE, BaresipOption.AUDIO_ALERT,
                                                                  BaresipOption.AUDIO_BUFFER,
                                                                  BaresipOption.AUDIO_BUFFER_MODE,
                                                                  BaresipOption.AUDIO_SILENCE,
                                                                  BaresipOption.AUDIO_JITTER_BUFFER_MS,
                                                                  BaresipOption.AUDIO_JITTER_BUFFER_TYPE,
                                                                  BaresipOption.AUDIO_JITTER_BUFFER_SIZE);

    // Five baresip-core options grouped on the Baresip tab:
    // call limits, timeouts, accept policy and SIP listen address
    private static final List<BaresipOption> BARESIP_TIER = List.of(BaresipOption.CALL_LOCAL_TIMEOUT,
                                                                    BaresipOption.CALL_MAX_CALLS,
                                                                    BaresipOption.RTP_TIMEOUT,
                                                                    BaresipOption.CALL_ACCEPT,
                                                                    BaresipOption.SIP_LISTEN);

    private static final List<BaresipOption> VIDEO_TIER = List.of(BaresipOption.VIDEO_CODEC,
                                                                  BaresipOption.VIDEO_RESOLUTION,
                                                                  BaresipOption.VIDEO_BITRATE, BaresipOption.VIDEO_FPS);

    private final Map<BaresipOption, String> pendingValues = new EnumMap<>(BaresipOption.class);

    /**
     * @param settingLoader lookup of persisted settings values by
     *                      settings-table key, typically
     *                      SettingsService::getSetting
     */
    public BaresipSettingsFormModel(Function<String, Optional<String>> settingLoader) {

        for (BaresipOption option : BaresipOption.values()) {

            pendingValues.put(option, settingLoader.apply(option.settingsKey()).orElse(option.defaultValue()));
        }
    }

    /**
     * Options shown on the Audio tab.
     */
    public static List<BaresipOption> audioTier() {

        return AUDIO_TIER;
    }

    /**
     * Options shown on the Baresip tab.
     */
    public static List<BaresipOption> baresipTier() {

        return BARESIP_TIER;
    }

    /**
     * Options shown on the Video tab.
     */
    public static List<BaresipOption> videoTier() {

        return VIDEO_TIER;
    }

    public String valueFor(BaresipOption option) {

        return pendingValues.get(option);
    }

    public void setValue(BaresipOption option, String value) {

        pendingValues.put(option, value);
    }

    /**
     * Registry validation errors keyed by option. Empty when
     * every pending value is acceptable.
     */
    public Map<BaresipOption, String> validationErrors() {

        Map<BaresipOption, String> errors = new EnumMap<>(BaresipOption.class);

        for (Map.Entry<BaresipOption, String> entry : pendingValues.entrySet()) {

            String error = entry.getKey().validate(entry.getValue());

            if (error != null) {
                errors.put(entry.getKey(), error);
            }
        }

        return errors;
    }

    public boolean isValid() {

        return validationErrors().isEmpty();
    }

    /**
     * Pending values keyed by settings-table key, ready for
     * BaresipSettingsFacade apply and preview calls.
     */
    public Map<String, String> toSettingsMap() {

        Map<String, String> map = new LinkedHashMap<>();

        for (BaresipOption option : BaresipOption.values()) {

            map.put(option.settingsKey(), pendingValues.get(option));
        }

        return map;
    }
}
